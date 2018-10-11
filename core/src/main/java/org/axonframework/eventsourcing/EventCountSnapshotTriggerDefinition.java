/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

import java.io.Serializable;

/**
 * Snapshotter trigger mechanism that counts the number of events to decide when to create a snapshot. A snapshot is
 * triggered when the number of events applied on an aggregate exceeds the given threshold.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class EventCountSnapshotTriggerDefinition implements SnapshotTriggerDefinition {

    private final Snapshotter snapshotter;
    private final int threshold;

    /**
     * Initialized the SnapshotTriggerDefinition to threshold snapshots using the given {@code snapshotter}
     * when {@code threshold} events have been applied to an Aggregate instance
     *
     * @param snapshotter the snapshotter to notify when a snapshot needs to be taken
     * @param threshold   the number of events that will threshold the creation of a snapshot event
     */
    public EventCountSnapshotTriggerDefinition(Snapshotter snapshotter, int threshold) {
        this.snapshotter = snapshotter;
        this.threshold = threshold;
    }

    @Override
    public SnapshotTrigger prepareTrigger(Class<?> aggregateType) {
        return new EventCountSnapshotTrigger(snapshotter, aggregateType, threshold);
    }

    @Override
    public SnapshotTrigger reconfigure(Class<?> aggregateType, SnapshotTrigger trigger) {
        if (trigger instanceof EventCountSnapshotTrigger) {
            ((EventCountSnapshotTrigger) trigger).setSnapshotter(snapshotter);
            return trigger;
        }
        return new EventCountSnapshotTrigger(snapshotter, aggregateType, threshold);
    }

    private static class EventCountSnapshotTrigger implements SnapshotTrigger, Serializable {

        private final Class<?> aggregateType;
        private final int threshold;

        private transient Snapshotter snapshotter;
        private int counter = 0;

        public EventCountSnapshotTrigger(Snapshotter snapshotter, Class<?> aggregateType, int threshold) {
            this.snapshotter = snapshotter;
            this.aggregateType = aggregateType;
            this.threshold = threshold;
        }

        @Override
        public void eventHandled(EventMessage<?> msg) {
            if (++counter >= threshold && msg instanceof DomainEventMessage) {
                if (CurrentUnitOfWork.isStarted()) {
                    CurrentUnitOfWork.get().onPrepareCommit(
                            u -> scheduleSnapshot((DomainEventMessage) msg));
                } else {
                    scheduleSnapshot((DomainEventMessage) msg);
                }
                counter = 0;
            }
        }

        protected void scheduleSnapshot(DomainEventMessage msg) {
            snapshotter.scheduleSnapshot(aggregateType, msg.getAggregateIdentifier());
            counter = 0;
        }

        @Override
        public void initializationFinished() {
        }

        public void setSnapshotter(Snapshotter snapshotter) {
            this.snapshotter = snapshotter;
        }
    }
}

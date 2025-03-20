/*
 * Copyright (c) 2010-2023. Axon Framework
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

import javax.annotation.Nonnull;

/**
 * Abstract implementation of the {@link org.axonframework.eventsourcing.SnapshotTrigger} that schedules snapshots on
 * the Unit of Work. Actual logic when to schedule a snapshot should be provided by a subclass.
 *
 * @author Yvonne Ceelie
 * @since 4.4.4
 */
public abstract class AbstractSnapshotTrigger implements SnapshotTrigger {

    private transient Snapshotter snapshotter;
    private Class<?> aggregateType;
    private boolean initialized;

    /**
     * Instantiate a {@link AbstractSnapshotTrigger} based on the {@link Snapshotter} and aggregateType {@link Class<?>}.
     *
     * @param snapshotter   the {@link Snapshotter} for scheduling snapshots
     * @param aggregateType the {@link Class<?> of the aggregate that is creating a snapshot}
     */
    protected AbstractSnapshotTrigger(Snapshotter snapshotter, Class<?> aggregateType) {
        this.snapshotter = snapshotter;
        this.aggregateType = aggregateType;
        this.initialized = false;
    }

    @Override
    public void eventHandled(@Nonnull EventMessage<?> msg) {
        if (msg instanceof DomainEventMessage && exceedsThreshold()) {
            prepareSnapshotScheduling((DomainEventMessage<?>) msg);
            reset();
        }
    }

    @Override
    public void initializationFinished() {
        initialized = true;
    }

    private void prepareSnapshotScheduling(DomainEventMessage<?> eventMessage) {
        if (CurrentUnitOfWork.isStarted()) {
            if (initialized) {
                CurrentUnitOfWork.get().onPrepareCommit(
                        u -> scheduleSnapshot(eventMessage));
            } else {
                CurrentUnitOfWork.get().onCleanup(
                        u -> scheduleSnapshot(eventMessage));
            }
        } else {
            scheduleSnapshot(eventMessage);
        }
    }

    private void scheduleSnapshot(DomainEventMessage<?> eventMessage) {
        snapshotter.scheduleSnapshot(aggregateType, eventMessage.getAggregateIdentifier());
    }

    /**
     * Sets the snapshotter
     *
     * @param snapshotter The {@link Snapshotter} for scheduling snapshots.
     */
    public void setSnapshotter(Snapshotter snapshotter) {
        this.snapshotter = snapshotter;
    }

    /**
     * This method is used to determine if a new snapshot should be created
     * @return true if the threshold has been exceeded
     */
    protected abstract boolean exceedsThreshold();


    /**
     * This method is used to reset all the variables that are used to check if a threshold has been exceeded
     */
    protected abstract void reset();
}

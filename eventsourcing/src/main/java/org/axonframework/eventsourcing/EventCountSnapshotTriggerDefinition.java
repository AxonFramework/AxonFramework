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

import javax.annotation.Nonnull;

/**
 * Snapshotter trigger mechanism that counts the number of events to decide when to create a snapshot. A snapshot is
 * triggered when the number of events applied on an aggregate exceeds the given {@code threshold}.
 * <p>
 * This number can exceed in two distinct scenarios:
 * <ol>
 *     <li> When initializing / event sourcing the aggregate in question.</li>
 *     <li> When new events are being applied by the aggregate.</li>
 * </ol>
 * <p>
 * If the definable {@code threshold} is met in situation one, the snapshot will be triggered regardless of the outcome
 * of command handling. Thus also if command handling returns exceptionally. If the {@code threshold} is only reached
 * once the aggregate has been fully initialized, than the snapshot will only be triggered if handling resolves
 * successfully.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public class EventCountSnapshotTriggerDefinition implements SnapshotTriggerDefinition {

    private final Snapshotter snapshotter;
    private final int threshold;

    /**
     * Initialized the SnapshotTriggerDefinition to threshold snapshots using the given {@code snapshotter} when {@code
     * threshold} events have been applied to an Aggregate instance
     *
     * @param snapshotter the snapshotter to notify when a snapshot needs to be taken
     * @param threshold   the number of events that will threshold the creation of a snapshot event
     */
    public EventCountSnapshotTriggerDefinition(Snapshotter snapshotter, int threshold) {
        this.snapshotter = snapshotter;
        this.threshold = threshold;
    }

    @Override
    public SnapshotTrigger prepareTrigger(@Nonnull Class<?> aggregateType) {
        return new EventCountSnapshotTrigger(snapshotter, aggregateType, threshold);
    }

    @Override
    public SnapshotTrigger reconfigure(@Nonnull Class<?> aggregateType, @Nonnull SnapshotTrigger trigger) {
        if (trigger instanceof EventCountSnapshotTrigger) {
            ((EventCountSnapshotTrigger) trigger).setSnapshotter(snapshotter);
            return trigger;
        }
        return new EventCountSnapshotTrigger(snapshotter, aggregateType, threshold);
    }

    private static class EventCountSnapshotTrigger extends AbstractSnapshotTrigger {

        private final int threshold;
        private int counter = 0;

        public EventCountSnapshotTrigger(Snapshotter snapshotter, Class<?> aggregateType, int threshold) {
            super(snapshotter, aggregateType);
            this.threshold = threshold;
        }

        @Override
        public boolean exceedsThreshold() {
            return ++counter >= threshold;
        }

        @Override
        public void reset() {
            counter = 0;
        }

    }
}

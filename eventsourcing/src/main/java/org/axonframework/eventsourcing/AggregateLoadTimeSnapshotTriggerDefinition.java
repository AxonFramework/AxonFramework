/*
 * Copyright (c) 2010-2020. Axon Framework
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

import java.time.Clock;

/**
 * Snapshotter trigger mechanism that decides on the loading time of the Aggregate when to create a snapshot. A snapshot
 * is triggered when loading the aggregate exceeds the given {@code loadTimeMillisThreshold} in milliseconds.
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
 * @author Yvonne Ceelie
 * @since 4.4
 */
public class AggregateLoadTimeSnapshotTriggerDefinition implements SnapshotTriggerDefinition {

    private final Snapshotter snapshotter;
    private final long loadTimeMillisThreshold;
    static Clock clock = Clock.systemUTC();


    /**
     * Initialized the SnapshotTriggerDefinition to threshold snapshots using the given {@code snapshotter} when loading
     * the aggregate instance takes longer than {@code loadTimeMillisThreshold}
     *
     * @param snapshotter the snapshotter to notify when a snapshot needs to be taken
     * @param loadTimeMillisThreshold  the maximum time that loading an aggregate may take
     */
    public AggregateLoadTimeSnapshotTriggerDefinition(Snapshotter snapshotter, long loadTimeMillisThreshold) {
        this.snapshotter = snapshotter;
        this.loadTimeMillisThreshold = loadTimeMillisThreshold;
    }

    @Override
    public SnapshotTrigger prepareTrigger(Class<?> aggregateType) {
        return new AggregateLoadTimeSnapshotTrigger(snapshotter, aggregateType, loadTimeMillisThreshold);
    }

    @Override
    public SnapshotTrigger reconfigure(Class<?> aggregateType, SnapshotTrigger trigger) {
        if (trigger instanceof AggregateLoadTimeSnapshotTrigger) {
            ((AggregateLoadTimeSnapshotTrigger) trigger).setSnapshotter(snapshotter);
            return trigger;
        }
        return new AggregateLoadTimeSnapshotTrigger(snapshotter, aggregateType, loadTimeMillisThreshold);
    }

    private static class AggregateLoadTimeSnapshotTrigger extends AbstractSnapshotTrigger {

        private final long loadTimeMillisThreshold;
        private long startTime = clock.instant().toEpochMilli();

        public AggregateLoadTimeSnapshotTrigger(Snapshotter snapshotter, Class<?> aggregateType, long loadTimeMillisThreshold) {
            super(snapshotter, aggregateType);
            this.loadTimeMillisThreshold = loadTimeMillisThreshold;
        }

        public boolean exceedsThreshold(){
            return (clock.instant().toEpochMilli() - startTime) > loadTimeMillisThreshold;
        }

        public void reset() {
            startTime = clock.instant().toEpochMilli();
        }
    }
}

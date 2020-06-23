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

package org.axonframework.eventsourcing.snapshotting;

import org.axonframework.eventhandling.DomainEventData;

import java.util.function.Predicate;

/**
 * Functional interface defining an {@link #allow(DomainEventData)} method to take snapshot data into account when
 * loading an aggregate. When providing an instance of this, take the following into account:
 * <ol>
 *     <li> Only return {@code false} if the snapshot data belongs to the corresponding aggregate <b>and</b> it does no conform to the desired format.</li>
 *     <li> Return {@code true} if the snapshot data belongs to the corresponding aggregate and conforms to the desired format.</li>
 *     <li> Return {@code true} if the snapshot data <b>does not</b> correspond to the desired aggregate.</li>
 * </ol>
 * <p>
 * Whether the {@code DomainEventData} corresponds to the right aggregate and is of the desired format, is dependent on
 * the {@link org.axonframework.eventsourcing.Snapshotter} instance being used. By default, the {@link
 * org.axonframework.eventsourcing.AggregateSnapshotter} instances would be used.
 * <p>
 * In such a default set up, {@code DomainEventData} <i>corresponding to the right aggregate</i> means that the {@link
 * DomainEventData#getType()} matches the aggregate's type. If {@code DomainEventData} is of the <i>desired format</i>
 * should be based on the {@link DomainEventData#getPayload()}, which contains the entire aggregate state.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
@FunctionalInterface
public interface SnapshotFilter extends Predicate<DomainEventData<?>> {

    /**
     * Function applied to filter out snapshot data in the form of a {@link DomainEventData}. Return {@code true} if the
     * data should be kept and {@code false} if it should be dropped.
     *
     * @param snapshotData the snapshot data to verify for filtering
     * @return {@code true} if the data should be kept and {@code false} if it should be dropped
     */
    default boolean allow(DomainEventData<?> snapshotData) {
        return test(snapshotData);
    }

    /**
     * Combines {@code this} {@link SnapshotFilter} with the give {@code other} filter in an "AND" operation,
     * effectively validating whether both return {@code true} on a {@link #allow(DomainEventData)} call of each.
     *
     * @param other another {@link SnapshotFilter} instance to combine with {@code this} filter in an "AND" operation
     * @return a new {@link SnapshotFilter} combining the {@code this} and {@code other} filters in an "AND" operation
     */
    default SnapshotFilter combine(SnapshotFilter other) {
        return snapshotData -> this.allow(snapshotData) && other.allow(snapshotData);
    }

    /**
     * A {@link SnapshotFilter} implementation which allows all snapshots.
     *
     * @return {@link SnapshotFilter} implementation which allows all snapshots
     */
    static SnapshotFilter allowAll() {
        return snapshotData -> true;
    }

    /**
     * A {@link SnapshotFilter} implementation which rejects all snapshots.
     *
     * @return a {@link SnapshotFilter} implementation which rejects all snapshots
     */
    static SnapshotFilter rejectAll() {
        return snapshotData -> false;
    }
}

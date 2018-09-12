/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventsourcing.DomainEventMessage;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Functional interface that defines a method for choosing the correct snapshot (if any) within the stream of snapshots.
 *
 * @author Milan Savic
 * @since 4.0
 */
@FunctionalInterface
public interface SnapshotResolver {

    /**
     * Chooses the correct snapshot (if any) within the stream of given {@code snapshots}.
     *
     * @param snapshots A stream of snapshots for given aggregate
     * @return at most one chosen snapshot from a stream of given {@code snapshots}.
     */
    Optional<DomainEventMessage<?>> resolve(Stream<DomainEventMessage<?>> snapshots);

    /**
     * Implementation of {@link SnapshotResolver} which takes the last snapshot taken in the system.
     *
     * @return resolver which returns the last snapshot
     */
    static SnapshotResolver resolveLast() {
        return snapshots -> snapshots.max(Comparator.comparingLong(DomainEventMessage::getSequenceNumber));
    }
}

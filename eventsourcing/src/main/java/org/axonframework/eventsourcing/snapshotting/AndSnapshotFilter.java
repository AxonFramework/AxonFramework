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

/**
 * A {@link SnapshotFilter} implementation combining two other {@code SnapshotFilter}s through an "AND" operation.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class AndSnapshotFilter implements SnapshotFilter {

    private final SnapshotFilter first;
    private final SnapshotFilter second;

    /**
     * Construct a {@link AndSnapshotFilter} based on the given {@code first} and {@code second} {@link SnapshotFilter}
     * instances.
     *
     * @param first  a {@link SnapshotFilter} to combine with the {@code second} filter
     * @param second a {@link SnapshotFilter} to combine with the {@code first} filter
     */
    public AndSnapshotFilter(SnapshotFilter first, SnapshotFilter second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean filter(DomainEventData<?> snapshotData) {
        return first.filter(snapshotData) && second.filter(snapshotData);
    }
}

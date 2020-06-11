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
 * A {@link SnapshotFilter} implementation negating a given other {@code SnapshotFilter}.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class NegateSnapshotFilter implements SnapshotFilter {

    private final SnapshotFilter filter;

    /**
     * Construct a {@link NegateSnapshotFilter} which negates the outcome of the given {@code filter}.
     *
     * @param filter the {@link SnapshotFilter} to negate the result of
     */
    public NegateSnapshotFilter(SnapshotFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean filter(DomainEventData<?> snapshotData) {
        return !filter.filter(snapshotData);
    }
}

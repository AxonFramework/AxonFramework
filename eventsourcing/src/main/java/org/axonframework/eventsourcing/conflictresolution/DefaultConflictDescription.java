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

package org.axonframework.eventsourcing.conflictresolution;

import org.axonframework.eventhandling.DomainEventMessage;

import java.util.List;

/**
 * {@link ConflictDescription} implementation that takes the necessary information in the Constructor.
 */
public class DefaultConflictDescription implements ConflictDescription {

    private final String aggregateIdentifier;
    private final long expectedVersion;
    private final long actualVersion;
    private final List<DomainEventMessage<?>> unexpectedEvents;

    /**
     * Initialize the instance using given values.
     *
     * @param aggregateIdentifier The identifier of the conflicting aggregate
     * @param expectedVersion     The expected version of the aggregate
     * @param actualVersion       The actual version of the aggregate
     * @param unexpectedEvents    The events registered since the expected version
     */
    public DefaultConflictDescription(String aggregateIdentifier, long expectedVersion, long actualVersion,
                                      List<DomainEventMessage<?>> unexpectedEvents) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
        this.unexpectedEvents = unexpectedEvents;
    }

    @Override
    public String aggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public long expectedVersion() {
        return expectedVersion;
    }

    @Override
    public long actualVersion() {
        return actualVersion;
    }

    @Override
    public List<DomainEventMessage<?>> unexpectedEvents() {
        return unexpectedEvents;
    }
}

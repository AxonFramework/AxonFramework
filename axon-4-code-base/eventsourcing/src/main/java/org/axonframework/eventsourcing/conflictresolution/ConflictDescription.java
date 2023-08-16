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
 * Descries a conflict between expected and actual version of an aggregate.
 *
 * @author Allard Buijze
 * @since 3.2
 */
public interface ConflictDescription {

    /**
     * The identifier of the conflicting aggregate, as a String.
     *
     * @return the identifier of the conflicting aggregate
     */
    String aggregateIdentifier();

    /**
     * The expected version, as indicated when loading the aggregate
     *
     * @return the expected version
     */
    long expectedVersion();

    /**
     * The actual version of the loaded aggregate
     *
     * @return the actual version of the aggregate
     */
    long actualVersion();

    /**
     * The list of events that have been registered with this aggregate since the expected version.
     *
     * @return the events that were registered since the expected version of the aggregate
     */
    List<DomainEventMessage<?>> unexpectedEvents();
}

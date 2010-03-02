/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.repository.eventsourcing;

import org.axonframework.core.DomainEventStream;

import java.util.UUID;

/**
 * Abstraction of the event storage mechanism. Domain Events are stored and read as {@link
 * org.axonframework.core.DomainEventStream streams}.
 *
 * @author Allard Buijze
 * @since 0.1
 */
public interface EventStore {

    /**
     * Append the events in the given {@link org.axonframework.core.DomainEventStream stream} to the events available.
     *
     * @param type   The type descriptor of the object to store
     * @param events The event stream containing the events to store
     * @throws EventStoreException if an error occurs while storing the events in the event stream
     */
    void appendEvents(String type, DomainEventStream events);

    /**
     * Read the events of the aggregate identified by the given type and identifier.
     *
     * @param type       The type descriptor of the object to retrieve
     * @param identifier The unique aggregate identifier of the events to load
     * @return an event stream containing the events of the aggregate
     *
     * @throws EventStoreException if an error occurs while reading the events in the event stream
     */
    DomainEventStream readEvents(String type, UUID identifier);
}

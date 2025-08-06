/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Generic implementation of a serialized domain event entry. This implementation can be used by Event Storage Engine
 * implementations to reconstruct an event or snapshot from the underlying storage for example.
 *
 * @author Rene de Waele
 */
public class GenericDomainEventEntry<P> extends AbstractDomainEventEntry<P> {

    /**
     * Constructs an {@code GenericDomainEventEntry} with the given parameters.
     *
     * @param eventIdentifier         The identifier of the event.
     * @param payloadType             The fully qualified class name or alias of the event payload.
     * @param payloadRevision         The revision of the event payload.
     * @param payload                 The serialized payload.
     * @param metaData                The serialized metadata.
     * @param timestamp               The time at which the event was originally created.
     * @param aggregateType           The type of aggregate that published this event.
     * @param aggregateIdentifier     The identifier of the aggregate that published this event.
     * @param aggregateSequenceNumber The sequence number of the event in the aggregate.
     */
    public GenericDomainEventEntry(@Nonnull String eventIdentifier,
                                   @Nonnull String payloadType,
                                   @Nonnull String payloadRevision,
                                   @Nullable P payload,
                                   @Nullable P metaData,
                                   @Nonnull Object timestamp,
                                   @Nonnull String aggregateType,
                                   @Nonnull String aggregateIdentifier,
                                   long aggregateSequenceNumber) {
        super(eventIdentifier,
              payloadType,
              payloadRevision,
              payload,
              metaData,
              timestamp,
              aggregateType,
              aggregateIdentifier,
              aggregateSequenceNumber);
    }
}

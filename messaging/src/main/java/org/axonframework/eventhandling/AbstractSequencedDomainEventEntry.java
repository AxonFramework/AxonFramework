/*
 * Copyright (c) 2010-2022. Axon Framework
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

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.axonframework.serialization.Serializer;

/**
 * Abstract base class of a serialized domain event. Fields in this class contain JPA annotations that direct JPA event
 * storage engines how to store event entries.
 *
 * @author Rene de Waele
 */
@MappedSuperclass
public abstract class AbstractSequencedDomainEventEntry<T> extends AbstractDomainEventEntry<T> implements DomainEventData<T> {

    @Id
    @GeneratedValue
    @SuppressWarnings("unused")
    private long globalIndex;

    /**
     * Construct a new default domain event entry from a published domain event message to enable storing the event or
     * sending it to a remote location. The event payload and metadata will be serialized to a byte array.
     * <p>
     * The given {@code serializer} will be used to serialize the payload and metadata in the given {@code
     * eventMessage}. The type of the serialized data will be the same as the given {@code contentType}.
     *
     * @param eventMessage The event message to convert to a serialized event entry
     * @param serializer   The serializer to convert the event
     * @param contentType  The data type of the payload and metadata after serialization
     */
    public AbstractSequencedDomainEventEntry(DomainEventMessage<?> eventMessage, Serializer serializer,
                                             Class<T> contentType) {
        super(eventMessage, serializer, contentType);
    }

    /**
     * Default constructor required by JPA
     */
    protected AbstractSequencedDomainEventEntry() {
    }
}

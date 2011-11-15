/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.domain;

/**
 * Represents a Message that wrapps a DomainEvent, and Event representing an important change in the Domain. In
 * contrast to a regular EventMessage, a DomainEventMessages contains the identifier of the Aggregate that reported it.
 * The DomainEventMessage's sequence number allows Messages to be placed in their order of generation.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface DomainEventMessage<T> extends EventMessage<T> {

    /**
     * Returns the sequence number that allows DomainEvents originating from the same Aggregate to be placed in the
     * order of generation.
     *
     * @return the sequence number of this Event
     */
    Long getSequenceNumber();

    /**
     * Returns the identifier of the Aggregate that generated this DomainEvent.
     *
     * @return the identifier of the Aggregate that generated this DomainEvent
     */
    AggregateIdentifier getAggregateIdentifier();

    /**
     * Returns a copy of this DomainEventMessage with the given <code>metaData</code>. The payload, {@link
     * #getTimestamp()
     * Timestamp} and {@link #getEventIdentifier() EventIdentifier}, as well as the {@link #getAggregateIdentifier()
     * Aggregate Identifier} and {@link #getSequenceNumber() Sequence Number} remain unchanged.
     *
     * @param metaData The new MetaData for the Message
     * @return a copy of this message with the given MetaData
     */
    @Override
    DomainEventMessage<T> withMetaData(MetaData metaData);
}

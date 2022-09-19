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

import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Represents a Message that wraps a DomainEvent and an Event representing an important change in the Domain. In
 * contrast to a regular EventMessage, a DomainEventMessages contains the identifier of the Aggregate that reported it.
 * The DomainEventMessage's sequence number allows Messages to be placed in their order of generation.
 *
 * @param <T> The type of payload contained in this Message
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
    long getSequenceNumber();

    /**
     * Returns the identifier of the Aggregate that generated this DomainEvent. Note that the value returned does not
     * necessarily have to be the same instance that was provided at creation time. It is possible that (due to
     * serialization, for example) the value returned here has a different structure.
     *
     * @return the identifier of the Aggregate that generated this DomainEvent
     */
    String getAggregateIdentifier();

    /**
     * Returns the type of the Aggregate that generated this DomainEvent. By default this is equal to the simple class
     * name of the aggregate root.
     *
     * @return the type of the Aggregate that generated this DomainEvent
     */
    String getType();

    /**
     * Returns a copy of this DomainEventMessage with the given {@code metaData}. The payload, {@link #getTimestamp()
     * Timestamp} and {@link #getIdentifier() EventIdentifier}, as well as the {@link #getAggregateIdentifier()
     * Aggregate Identifier} and {@link #getSequenceNumber() Sequence Number} remain unchanged.
     *
     * @param metaData The new MetaData for the Message
     * @return a copy of this message with the given MetaData
     */
    @Override
    DomainEventMessage<T> withMetaData(@Nonnull Map<String, ?> metaData);

    /**
     * Returns a copy of this DomainEventMessage with its MetaData merged with the given {@code metaData}. The payload,
     * {@link #getTimestamp() Timestamp} and {@link #getIdentifier() EventIdentifier}, as well as the {@link
     * #getAggregateIdentifier() Aggregate Identifier} and {@link #getSequenceNumber() Sequence Number} remain
     * unchanged.
     *
     * @param metaData The MetaData to merge with
     * @return a copy of this message with the given MetaData
     */
    @Override
    DomainEventMessage<T> andMetaData(@Nonnull Map<String, ?> metaData);
}

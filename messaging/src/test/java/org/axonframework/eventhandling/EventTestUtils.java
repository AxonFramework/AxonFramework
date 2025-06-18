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
import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Test utilities when dealing with events.
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 */
public abstract class EventTestUtils {

    private EventTestUtils() {
        // Utility class
    }

    /**
     * Constructs an {@link EventMessage} with the given {@code seq} as the {@link EventMessage#getPayload() payload}.
     *
     * @param seq The payload for the message to construct.
     * @return An {@link EventMessage} with the given {@code seq} as the {@link EventMessage#getPayload() payload}.
     */
    public static EventMessage<?> eventMessage(int seq) {
        return EventTestUtils.asEventMessage("Event[" + seq + "]");
    }

    /**
     * Returns the given {@code event} wrapped in an {@link EventMessage}.
     * <p>
     * If {@code event} already implements {@code EventMessage}, it is returned as-is. If it is a {@link Message}, a new
     * {@code EventMessage} will be created using the payload and metadata of the given message. Otherwise, the given
     * {@code event} is wrapped into a {@link GenericEventMessage} as its payload.
     *
     * @param event The event to wrap as {@link EventMessage}.
     * @param <P>   The generic type of the expected payload of the resulting object.
     * @return An {@link EventMessage} containing given {@code event} as payload, or {@code event} if it already
     * implements {@code EventMessage}.
     */
    @SuppressWarnings("unchecked")
    public static <P> EventMessage<P> asEventMessage(@Nonnull Object event) {
        if (event instanceof EventMessage) {
            return (EventMessage<P>) event;
        } else if (event instanceof Message) {
            Message<P> message = (Message<P>) event;
            return new GenericEventMessage<>(message, GenericEventMessage.clock.instant());
        }
        return new GenericEventMessage<>(
                new GenericMessage<>(new MessageType(event.getClass()), (P) event),
                GenericEventMessage.clock.instant()
        );
    }

    private static final MessageType TYPE = new MessageType("event");
    private static final String PAYLOAD = "payload";
    public static final String AGGREGATE = "aggregate";
    private static final String AGGREGATE_TYPE = "aggregateType";
    private static final MetaData METADATA = MetaData.emptyInstance();

    public static List<DomainEventMessage<?>> createDomainEvents(int numberOfEvents) {
        return IntStream.range(0, numberOfEvents)
                        .mapToObj(sequenceNumber -> createDomainEvent(AGGREGATE_TYPE,
                                                                      IdentifierFactory.getInstance()
                                                                                       .generateIdentifier(),
                                                                      AGGREGATE,
                                                                      sequenceNumber,
                                                                      PAYLOAD + sequenceNumber,
                                                                      METADATA))
                        .collect(Collectors.toList());
    }

    public static DomainEventMessage<String> createDomainEvent() {
        return createDomainEvent(0);
    }

    public static DomainEventMessage<String> createDomainEvent(long sequenceNumber) {
        return createDomainEvent(AGGREGATE, sequenceNumber);
    }

    public static DomainEventMessage<String> createDomainEvent(long sequenceNumber, Instant timestamp) {
        return new GenericDomainEventMessage<>(
                AGGREGATE_TYPE, AGGREGATE, sequenceNumber,
                IdentifierFactory.getInstance().generateIdentifier(), TYPE,
                PAYLOAD, METADATA, timestamp
        );
    }

    public static DomainEventMessage<String> createDomainEvent(String aggregateId, long sequenceNumber) {
        return createDomainEvent(aggregateId, sequenceNumber, PAYLOAD);
    }

    public static DomainEventMessage<String> createDomainEvent(String aggregateId, long sequenceNumber,
                                                               String payload) {
        return createDomainEvent(AGGREGATE_TYPE,
                                 IdentifierFactory.getInstance().generateIdentifier(),
                                 aggregateId,
                                 sequenceNumber,
                                 payload,
                                 METADATA);
    }

    public static DomainEventMessage<String> createDomainEvent(String eventId, String aggregateId,
                                                               long sequenceNumber) {
        return createDomainEvent(AGGREGATE_TYPE, eventId, aggregateId, sequenceNumber, PAYLOAD, METADATA);
    }

    public static DomainEventMessage<String> createDomainEvent(String type,
                                                               String eventId,
                                                               String aggregateId,
                                                               long sequenceNumber,
                                                               String payload,
                                                               MetaData metaData) {
        return new GenericDomainEventMessage<>(type,
                                               aggregateId,
                                               sequenceNumber,
                                               eventId,
                                               TYPE,
                                               payload,
                                               metaData,
                                               GenericDomainEventMessage.clock.instant());
    }
}

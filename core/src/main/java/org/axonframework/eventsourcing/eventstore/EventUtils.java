/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.GenericTrackedDomainEventMessage;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.UnknownSerializedTypeException;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.InitialEventRepresentation;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Spliterator.*;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

/**
 * Utility class for dealing with events and event streams.
 *
 * @author Rene de Waele
 */
public abstract class EventUtils {

    private static final Logger logger = LoggerFactory.getLogger(EventUtils.class);

    /**
     * Convert an {@link EventMessage} to a {@link TrackedEventMessage} using the given {@code trackingToken}. If the
     * event is a {@link DomainEventMessage} the message will be converted to a {@link
     * GenericTrackedDomainEventMessage}, otherwise a {@link GenericTrackedEventMessage} is returned.
     *
     * @param eventMessage  the message to convert
     * @param trackingToken the tracking token to use for the resulting message
     * @param <T>           the payload type of the event
     * @return the message converted to a tracked event messge
     */
    public static <T> TrackedEventMessage<T> asTrackedEventMessage(EventMessage<T> eventMessage,
                                                                   TrackingToken trackingToken) {
        if (eventMessage instanceof DomainEventMessage<?>) {
            return new GenericTrackedDomainEventMessage<>(trackingToken, (DomainEventMessage<T>) eventMessage);
        }
        return new GenericTrackedEventMessage<>(trackingToken, eventMessage);
    }

    /**
     * Convert a plain {@link EventMessage} to a {@link DomainEventMessage}. If the message already is a {@link
     * DomainEventMessage} it will be returned as is. Otherwise a new {@link GenericDomainEventMessage} is made with
     * {@code null} type, aggegrateIdentifier equal to messageIdentifier and sequence number of 0L.
     *
     * @param eventMessage the input event message
     * @param <T>          The type of payload in the message
     * @return the message converted to a domain event message
     */
    public static <T> DomainEventMessage<T> asDomainEventMessage(EventMessage<T> eventMessage) {
        if (eventMessage instanceof DomainEventMessage<?>) {
            return (DomainEventMessage<T>) eventMessage;
        }
        return new GenericDomainEventMessage<>(null, eventMessage.getIdentifier(), 0L, eventMessage,
                                               eventMessage::getTimestamp);
    }

    /**
     * Upcasts and deserializes the given {@code eventEntryStream} using the given {@code serializer} and
     * {@code upcasterChain}.
     * <p>
     * The list of events returned contains lazy deserializing events for optimization purposes. Events represented with
     * unknown classes are ignored if {@code skipUnknownTypes} is {@code true}
     *
     * @param eventEntryStream the stream of entries containing the data of the serialized event
     * @param serializer       the serializer to deserialize the event with
     * @param upcasterChain    the chain containing the upcasters to upcast the events with
     * @param skipUnknownTypes whether unknown serialized types should be ignored
     * @return a stream of lazy deserializing events
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static DomainEventStream upcastAndDeserializeDomainEvents(
            Stream<? extends DomainEventData<?>> eventEntryStream, Serializer serializer, EventUpcaster upcasterChain,
            boolean skipUnknownTypes) {
        AtomicReference<Long> currentSequenceNumber = new AtomicReference<>();
        Stream<IntermediateEventRepresentation> upcastResult =
                upcastAndDeserialize(eventEntryStream, serializer, upcasterChain, skipUnknownTypes, entry -> {
                    InitialEventRepresentation result = new InitialEventRepresentation(entry, serializer);
                    currentSequenceNumber.set(result.getSequenceNumber().get());
                    return result;
                });
        Stream<? extends DomainEventMessage<?>> stream = upcastResult.map(ir -> {
            SerializedMessage<?> serializedMessage = new SerializedMessage<>(ir.getMessageIdentifier(),
                                                                             new LazyDeserializingObject<>(
                                                                                     ir::getData,
                                                                                     ir.getType(), serializer),
                                                                             ir.getMetaData());
            if (ir.getTrackingToken().isPresent()) {
                return new GenericTrackedDomainEventMessage<>(ir.getTrackingToken().get(), ir.getAggregateType().get(),
                                                              ir.getAggregateIdentifier().get(),
                                                              ir.getSequenceNumber().get(), serializedMessage,
                                                              ir::getTimestamp);
            } else {
                return new GenericDomainEventMessage<>(ir.getAggregateType().get(), ir.getAggregateIdentifier().get(),
                                                       ir.getSequenceNumber().get(), serializedMessage,
                                                       ir::getTimestamp);
            }
        });
        return DomainEventStream.of(stream, currentSequenceNumber::get);
    }

    /**
     * Upcasts and deserializes the given {@code eventEntryStream} using the given {@code serializer} and
     * {@code upcasterChain}.
     * <p>
     * The list of events returned contains lazy deserializing events for optimization purposes. Events represented with
     * unknown classes are ignored if {@code skipUnknownTypes} is {@code true}
     *
     * @param eventEntryStream the stream of entries containing the data of the serialized event
     * @param serializer       the serializer to deserialize the event with
     * @param upcasterChain    the chain containing the upcasters to upcast the events with
     * @param skipUnknownTypes whether unknown serialized types should be ignored
     * @return a stream of lazy deserializing events
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static Stream<TrackedEventMessage<?>> upcastAndDeserializeTrackedEvents(
            Stream<? extends TrackedEventData<?>> eventEntryStream, Serializer serializer, EventUpcaster upcasterChain,
            boolean skipUnknownTypes) {
        Stream<IntermediateEventRepresentation> upcastResult =
                upcastAndDeserialize(eventEntryStream, serializer, upcasterChain, skipUnknownTypes,
                                     entry -> new InitialEventRepresentation(entry, serializer));
        return upcastResult.map(ir -> {
            SerializedMessage<?> serializedMessage = new SerializedMessage<>(ir.getMessageIdentifier(),
                                                                             new LazyDeserializingObject<>(
                                                                                     ir::getData,
                                                                                     ir.getType(), serializer),
                                                                             ir.getMetaData());
            if (ir.getAggregateIdentifier().isPresent()) {
                return new GenericTrackedDomainEventMessage<>(ir.getTrackingToken().get(),
                                                              ir.getAggregateType().orElse(null),
                                                              ir.getAggregateIdentifier().get(),
                                                              ir.getSequenceNumber().get(), serializedMessage,
                                                              ir::getTimestamp);
            } else {
                return new GenericTrackedEventMessage<>(ir.getTrackingToken().get(), serializedMessage,
                                                        ir::getTimestamp);
            }
        });
    }

    /**
     * Convert the given {@code domainEventStream} to a regular java {@link Stream} of domain event messages.
     *
     * @param domainEventStream the input {@link DomainEventStream}
     * @return the output {@link Stream} after conversion
     */
    public static Stream<? extends DomainEventMessage<?>> asStream(DomainEventStream domainEventStream) {
        return stream(spliteratorUnknownSize(domainEventStream, DISTINCT | NONNULL | ORDERED), false);
    }

    private static Stream<IntermediateEventRepresentation> upcastAndDeserialize(
            Stream<? extends EventData<?>> eventEntryStream, Serializer serializer, EventUpcaster upcasterChain,
            boolean skipUnknownTypes, Function<EventData<?>, IntermediateEventRepresentation> entryConverter) {
        Stream<IntermediateEventRepresentation> upcastResult =
                upcasterChain.upcast(eventEntryStream.map(entryConverter));
        if (skipUnknownTypes) {
            upcastResult = upcastResult.filter(ir -> {
                try {
                    serializer.classForType(ir.getType());
                    return true;
                } catch (UnknownSerializedTypeException e) {
                    return false;
                }
            });
        }
        return upcastResult;
    }

    private EventUtils() {
    }
}

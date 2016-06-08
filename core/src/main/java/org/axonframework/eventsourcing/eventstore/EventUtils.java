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
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.upcasting.event.InitialEventRepresentation;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Spliterator.*;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Rene de Waele
 */
public abstract class EventUtils {

    private static final Logger logger = LoggerFactory.getLogger(EventUtils.class);

    public static <T> TrackedEventMessage<T> asTrackedEventMessage(EventMessage<T> eventMessage,
                                                                   TrackingToken trackingToken) {
        if (eventMessage instanceof DomainEventMessage<?>) {
            return new GenericTrackedDomainEventMessage<>(trackingToken, (DomainEventMessage<T>) eventMessage);
        }
        return new GenericTrackedEventMessage<>(trackingToken, eventMessage);
    }

    public static DomainEventMessage<?> asDomainEventMessage(EventMessage<?> eventMessage) {
        if (eventMessage instanceof DomainEventMessage<?>) {
            return (DomainEventMessage<?>) eventMessage;
        }
        return new GenericDomainEventMessage<>(null, eventMessage.getIdentifier(), 0L, eventMessage,
                                               eventMessage.getTimestamp());
    }

    /**
     * Upcasts and deserializes the given <code>eventEntryStream</code> using the given <code>serializer</code> and
     * <code>upcasterChain</code>.
     * <p>
     * The list of events returned contains lazy deserializing events for optimization purposes. Events represented with
     * unknown classes are ignored if <code>skipUnknownTypes</code> is <code>true</code>
     *
     * @param eventEntryStream the stream of entries containing the data of the serialized event
     * @param serializer       the serializer to deserialize the event with
     * @param upcasterChain    the chain containing the upcasters to upcast the events with
     * @param skipUnknownTypes whether unknown serialized types should be ignored
     * @return a stream of lazy deserializing events
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static Stream<DomainEventMessage<?>> upcastAndDeserializeDomainEvents(
            Stream<? extends DomainEventData<?>> eventEntryStream, Serializer serializer,
            EventUpcasterChain upcasterChain, boolean skipUnknownTypes) {
        Stream<IntermediateEventRepresentation> upcastResult =
                upcastAndDeserialize(eventEntryStream, serializer, upcasterChain, skipUnknownTypes);
        return upcastResult.map(ir -> {
            SerializedMessage<?> serializedMessage = new SerializedMessage<>(ir.getMessageIdentifier(),
                                                                             new LazyDeserializingObject<>(ir::getOutputData,
                                                                                                     ir.getOutputType(),
                                                                                                     serializer),
                                                                             ir.getMetaData());
            return new GenericDomainEventMessage<>(ir.getAggregateType().get(), ir.getAggregateIdentifier().get(),
                                                   ir.getSequenceNumber().get(), serializedMessage, ir.getTimestamp());
        });
    }

    /**
     * Upcasts and deserializes the given <code>eventEntryStream</code> using the given <code>serializer</code> and
     * <code>upcasterChain</code>.
     * <p>
     * The list of events returned contains lazy deserializing events for optimization purposes. Events represented with
     * unknown classes are ignored if <code>skipUnknownTypes</code> is <code>true</code>
     *
     * @param eventEntryStream the stream of entries containing the data of the serialized event
     * @param serializer       the serializer to deserialize the event with
     * @param upcasterChain    the chain containing the upcasters to upcast the events with
     * @param skipUnknownTypes whether unknown serialized types should be ignored
     * @return a stream of lazy deserializing events
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static Stream<TrackedEventMessage<?>> upcastAndDeserializeTrackedEvents(
            Stream<? extends TrackedEventData<?>> eventEntryStream, Serializer serializer,
            EventUpcasterChain upcasterChain, boolean skipUnknownTypes) {
        Stream<IntermediateEventRepresentation> upcastResult =
                upcastAndDeserialize(eventEntryStream, serializer, upcasterChain, skipUnknownTypes);
        return upcastResult.map(ir -> {
            SerializedMessage<?> serializedMessage = new SerializedMessage<>(ir.getMessageIdentifier(),
                                                                             new LazyDeserializingObject<>(ir::getOutputData,
                                                                                                     ir.getOutputType(),
                                                                                                     serializer),
                                                                             ir.getMetaData());
            if (ir.getAggregateIdentifier().isPresent()) {
                return new GenericTrackedDomainEventMessage<>(ir.getTrackingToken().get(), ir.getAggregateType().get(),
                                                              ir.getAggregateIdentifier().get(),
                                                              ir.getSequenceNumber().get(), serializedMessage,
                                                              ir.getTimestamp());
            } else {
                return new GenericTrackedEventMessage<>(ir.getTrackingToken().get(), serializedMessage,
                                                        ir.getTimestamp());
            }
        });
    }

    private static Stream<IntermediateEventRepresentation> upcastAndDeserialize(
            Stream<? extends EventData<?>> eventEntryStream, Serializer serializer, EventUpcasterChain upcasterChain,
            boolean skipUnknownTypes) {
        Stream<IntermediateEventRepresentation> upcastResult =
                eventEntryStream.map(entry -> new InitialEventRepresentation(entry, serializer));
        upcastResult = upcasterChain.upcast(upcastResult);
        if (skipUnknownTypes) {
            upcastResult.filter(ir -> {
                try {
                    serializer.classForType(ir.getOutputType());
                    return true;
                } catch (UnknownSerializedTypeException e) {
                    return false;
                }
            });
        }
        return upcastResult;
    }

    public static Stream<? extends DomainEventMessage<?>> asStream(DomainEventStream domainEventStream) {
        return stream(spliteratorUnknownSize(domainEventStream, DISTINCT | NONNULL | ORDERED), false);
    }

    public static Stream<? extends TrackedEventMessage<?>> asStream(TrackingEventStream trackingEventStream) {
        Spliterator<? extends TrackedEventMessage<?>> spliterator =
                new Spliterators.AbstractSpliterator<TrackedEventMessage<?>>(Long.MAX_VALUE,
                                                                             DISTINCT | NONNULL | ORDERED) {
                    @Override
                    public boolean tryAdvance(Consumer<? super TrackedEventMessage<?>> action) {
                        try {
                            action.accept(trackingEventStream.nextAvailable());
                        } catch (InterruptedException e) {
                            logger.warn("Event stream interrupted", e);
                            Thread.currentThread().interrupt();
                            return false;
                        }
                        return true;
                    }
                };
        return stream(spliterator, false);
    }

    private EventUtils() {
    }
}

/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.InitialEventRepresentation;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;

import javax.annotation.Nonnull;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Utility class for dealing with events.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class EventUtils {

    /**
     * Convert an {@link EventMessage} to a {@link TrackedEventMessage} using the given {@code trackingToken}. If the
     * event is a {@link DomainEventMessage} the message will be converted to a
     * {@link GenericTrackedDomainEventMessage}, otherwise a {@link GenericTrackedEventMessage} is returned.
     *
     * @param eventMessage  the message to convert
     * @param trackingToken the tracking token to use for the resulting message
     * @param <T>           the payload type of the event
     * @return the message converted to a tracked event message
     */
    public static <T> TrackedEventMessage<T> asTrackedEventMessage(EventMessage<T> eventMessage,
                                                                   TrackingToken trackingToken) {
        if (eventMessage instanceof TrackedEventMessage) {
            return ((TrackedEventMessage<T>) eventMessage).withTrackingToken(trackingToken);
        }
        if (eventMessage instanceof DomainEventMessage<?>) {
            return new GenericTrackedDomainEventMessage<>(trackingToken, (DomainEventMessage<T>) eventMessage);
        }
        return new GenericTrackedEventMessage<>(trackingToken, eventMessage);
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
     * @return a stream of lazy deserializing events
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static Stream<TrackedEventMessage<?>> upcastAndDeserializeTrackedEvents(
            Stream<? extends TrackedEventData<?>> eventEntryStream,
            Serializer serializer,
            EventUpcaster upcasterChain
    ) {
        Stream<IntermediateEventRepresentation> upcastResult =
                upcastAndDeserialize(eventEntryStream, upcasterChain,
                                     entry -> new InitialEventRepresentation(entry, serializer));
        return upcastResult.map(ir -> {
            SerializedMessage<?> serializedMessage = new SerializedMessage<>(
                    ir.getMessageIdentifier(),
                    QualifiedNameUtils.fromClassName(serializer.classForType(ir.getType())),
                    new LazyDeserializingObject<>(ir::getData, ir.getType(), serializer),
                    ir.getMetaData()
            );
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

    private static Stream<IntermediateEventRepresentation> upcastAndDeserialize(
            Stream<? extends EventData<?>> eventEntryStream, EventUpcaster upcasterChain,
            Function<EventData<?>, IntermediateEventRepresentation> entryConverter) {
        return upcasterChain.upcast(eventEntryStream.map(entryConverter));
    }

    /**
     * Returns the given event as an EventMessage. If {@code event} already implements EventMessage, it is returned
     * as-is. If it is a Message, a new EventMessage will be created using the payload and meta data of the given
     * message. Otherwise, the given {@code event} is wrapped into a GenericEventMessage as its payload.
     *
     * <b>Note:</b> This method is intended for internal use only.
     *
     * @param event the event to wrap as EventMessage
     * @param <P>   The generic type of the expected payload of the resulting object
     * @return an EventMessage containing given {@code event} as payload, or {@code event} if it already implements
     * EventMessage.
     */
    @SuppressWarnings("unchecked")
    public static <E> EventMessage<E> asEventMessage(@Nonnull Object event, @Nonnull Function<Object, QualifiedName> nameResolver) {
        if (event instanceof EventMessage<?>) {
            return (EventMessage<E>) event;
        } else if (event instanceof Message<?>) {
            Message<E> message = (Message<E>) event;
            return new GenericEventMessage<>(message, () -> GenericEventMessage.clock.instant());
        }
        return new GenericEventMessage<>(
                nameResolver.apply(event),
                (E) event,
                MetaData.emptyInstance()
        );
    }

    private EventUtils() {
        // Utility class
    }
}

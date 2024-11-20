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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericTrackedDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.InitialEventRepresentation;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Spliterator.*;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

/**
 * Utility class for dealing with event streams.
 *
 * @author Steven van Beelen
 * @since 4.0
 */
public abstract class EventStreamUtils {

    private EventStreamUtils() {
        // Utility class
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
    public static DomainEventStream upcastAndDeserializeDomainEvents(
            Stream<? extends DomainEventData<?>> eventEntryStream,
            Serializer serializer,
            EventUpcaster upcasterChain
    ) {
        AtomicReference<Long> currentSequenceNumber = new AtomicReference<>();
        Stream<IntermediateEventRepresentation> upcastResult =
                upcastAndDeserialize(eventEntryStream, upcasterChain, entry -> {
                    InitialEventRepresentation result = new InitialEventRepresentation(entry, serializer);
                    currentSequenceNumber.set(result.getSequenceNumber().get());
                    return result;
                });
        Stream<? extends DomainEventMessage<?>> stream = upcastResult.map(ir -> {
            SerializedMessage<?> serializedMessage = new SerializedMessage<>(
                    ir.getMessageIdentifier(),
                    QualifiedName.className(serializer.classForType(ir.getType())),
                    new LazyDeserializingObject<>(ir::getData, ir.getType(), serializer),
                    ir.getMetaData()
            );
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
     * Convert the given {@code domainEventStream} to a regular java {@link Stream} of domain event messages.
     *
     * @param domainEventStream the input {@link DomainEventStream}
     * @return the output {@link Stream} after conversion
     */
    public static Stream<? extends DomainEventMessage<?>> asStream(DomainEventStream domainEventStream) {
        return stream(spliteratorUnknownSize(domainEventStream, DISTINCT | NONNULL | ORDERED), false);
    }

    private static Stream<IntermediateEventRepresentation> upcastAndDeserialize(
            Stream<? extends EventData<?>> eventEntryStream, EventUpcaster upcasterChain,
            Function<EventData<?>, IntermediateEventRepresentation> entryConverter) {
        return upcasterChain.upcast(eventEntryStream.map(entryConverter));
    }
}

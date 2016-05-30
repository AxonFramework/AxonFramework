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
import org.axonframework.messaging.axon3.SerializedMessage;
import org.axonframework.serialization.*;
import org.axonframework.serialization.upcasting.SerializedDomainEventUpcastingContext;
import org.axonframework.serialization.upcasting.UpcastDomainEventData;
import org.axonframework.serialization.upcasting.UpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
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
     * Upcasts and deserializes the given <code>entry</code> using the given <code>serializer</code> and
     * <code>upcasterChain</code>. This code is optimized to deserialize the meta-data only once in case it has been
     * used in the upcasting process.
     * <p>
     * The list of events returned contains lazy deserializing events for optimization purposes. Events represented with
     * unknown classes are ignored, and not returned.
     *
     * @param entry            the entry containing the data of the serialized event
     * @param serializer       the serializer to deserialize the event with
     * @param upcasterChain    the chain containing the upcasters to upcast the events with
     * @param skipUnknownTypes whether unknown serialized types should be ignored
     * @return a list of upcast and deserialized events
     */
    @SuppressWarnings("unchecked")
    public static List<DomainEventMessage<?>> upcastAndDeserialize(DomainEventData<?> entry,
                                                                   Serializer serializer, UpcasterChain upcasterChain,
                                                                   boolean skipUnknownTypes) {
        SerializedDomainEventUpcastingContext context = new SerializedDomainEventUpcastingContext(entry, serializer);
        List<SerializedObject> objects = upcasterChain.upcast(entry.getPayload(), context);
        List<DomainEventMessage<?>> events = new ArrayList<>(objects.size());
        for (SerializedObject object : objects) {
            try {
                DomainEventMessage<Object> message = new SerializedDomainEventMessage<>(
                        new UpcastDomainEventData(entry, entry.getAggregateIdentifier(), object), serializer);

                // prevents duplicate deserialization of meta data when it has already been access during upcasting
                if (context.getSerializedMetaData().isDeserialized()) {
                    message = message.withMetaData(context.getSerializedMetaData().getObject());
                }
                events.add(message);
            } catch (UnknownSerializedTypeException e) {
                if (!skipUnknownTypes) {
                    throw e;
                }
                logger.info("Ignoring event of unknown type {} (rev. {}), as it cannot be resolved to a Class",
                            object.getType().getName(), object.getType().getRevision());
            }
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    public static List<TrackedEventMessage<?>> upcastAndDeserialize(TrackedEventData<?> entry,
                                                                    Serializer serializer, UpcasterChain upcasterChain,
                                                                    boolean skipUnknownTypes) {
        //todo replace when new upcaster API is ready
        return singletonList(new GenericTrackedEventMessage<>(entry.trackingToken(),
                                                              new SerializedMessage<>(entry.getEventIdentifier(),
                                                                                      new LazyDeserializingObject<>(
                                                                                              entry.getPayload(),
                                                                                              serializer),
                                                                                      new LazyDeserializingObject<>(
                                                                                              entry.getMetaData(),
                                                                                              serializer)),
                                                              entry.getTimestamp()));
    }

    public static Stream<? extends DomainEventMessage<?>> asStream(DomainEventStream domainEventStream) {
        return stream(spliteratorUnknownSize(domainEventStream, DISTINCT | NONNULL | ORDERED), false);
    }

    public static Stream<? extends TrackedEventMessage<?>> asStream(TrackingEventStream trackingEventStream) {
        Spliterator<? extends TrackedEventMessage<?>> spliterator = new Spliterators.AbstractSpliterator<TrackedEventMessage<?>>(
                Long.MAX_VALUE, DISTINCT | NONNULL | ORDERED) {
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

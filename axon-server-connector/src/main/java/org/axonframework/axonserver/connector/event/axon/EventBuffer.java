/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.grpc.event.EventWithToken;
import org.axonframework.eventhandling.EventUtils;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.UnknownSerializedType;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Client-side buffer of messages received from the server. Once consumed from this buffer, the client is notified
 * of a permit being consumed, potentially triggering a permit refresh, if flow control is enabled.
 * <p>
 * This class is intended for internal use. Be cautious.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 */
public class EventBuffer implements TrackingEventStream {

    private static final Logger logger = LoggerFactory.getLogger(EventBuffer.class);

    private static final int DEFAULT_POLLING_TIME_MILLIS = 500;

    private final Serializer serializer;
    private final BlockingQueue<TrackedEventData<byte[]>> events;
    private final Iterator<TrackedEventMessage<?>> eventStream;
    private final long pollingTimeMillis;

    private TrackedEventData<byte[]> peekData;
    private TrackedEventMessage<?> peekEvent;
    private Consumer<EventBuffer> closeCallback;
    private volatile RuntimeException exception;
    private volatile boolean closed;
    private Consumer<Integer> consumeListener = i -> {
    };
    private volatile Consumer<SerializedType> blacklistListener;

    /**
     * Initializes an Event Buffer, passing messages through given {@code upcasterChain} and deserializing events using
     * given {@code serializer}.
     *
     * @param upcasterChain the upcasterChain to translate serialized representations before deserializing
     * @param serializer    the serializer capable of deserializing incoming messages
     */
    public EventBuffer(EventUpcaster upcasterChain, Serializer serializer) {
        this(upcasterChain, serializer, DEFAULT_POLLING_TIME_MILLIS);
    }

    /**
     * Initializes an Event Buffer, passing messages through given {@code upcasterChain} and deserializing events using
     * given {@code serializer}.
     *
     * @param upcasterChain     the upcasterChain to translate serialized representations before deserializing
     * @param serializer        the serializer capable of deserializing incoming messages
     * @param pollingTimeMillis a {@code long} defining the polling periods used to split the up the timeout used in
     *                          {@link #hasNextAvailable(int, TimeUnit)}, ensuring nobody accidentally blocks for an
     *                          extended period of time. Defaults to 500 milliseconds
     */
    public EventBuffer(EventUpcaster upcasterChain, Serializer serializer, long pollingTimeMillis) {
        this.serializer = serializer;
        this.events = new LinkedBlockingQueue<>();
        this.eventStream = EventUtils.upcastAndDeserializeTrackedEvents(
                StreamSupport.stream(new SimpleSpliterator<>(this::poll), false),
                new GrpcMetaDataAwareSerializer(serializer),
                getOrDefault(upcasterChain, NoOpEventUpcaster.INSTANCE)
        ).iterator();
        this.pollingTimeMillis = pollingTimeMillis;
    }

    private TrackedEventData<byte[]> poll() {
        TrackedEventData<byte[]> nextItem;
        if (peekData != null) {
            nextItem = peekData;
            peekData = null;
            return nextItem;
        }
        nextItem = events.poll();
        if (nextItem != null) {
            consumeListener.accept(1);
        }
        return nextItem;
    }

    private void waitForData(long deadline) throws InterruptedException {
        long now = System.currentTimeMillis();
        if (peekData == null && now < deadline) {
            peekData = events.poll(Math.min(deadline - now, pollingTimeMillis), TimeUnit.MILLISECONDS);
            if (peekData != null) {
                consumeListener.accept(1);
            }
        }
    }

    /**
     * {@inheritDoc}
     * ----
     * <p>
     * This implementation blacklists based on the payload type of the given message.
     */
    @Override
    public void blacklist(TrackedEventMessage<?> trackedEventMessage) {
        Consumer<SerializedType> bl = blacklistListener;
        if (bl == null) {
            return;
        }
        if (UnknownSerializedType.class.equals(trackedEventMessage.getPayloadType())) {
            UnknownSerializedType unknownSerializedType = (UnknownSerializedType) trackedEventMessage.getPayload();
            bl.accept(unknownSerializedType.serializedType());
        } else {
            bl.accept(serializer.typeForClass(trackedEventMessage.getPayloadType()));
        }
    }

    /**
     * Registers the callback to invoke when the reader wishes to close the stream.
     *
     * @param closeCallback The callback to invoke when the reader wishes to close the stream
     */
    public void registerCloseListener(Consumer<EventBuffer> closeCallback) {
        this.closeCallback = closeCallback;
    }

    /**
     * Registers the callback to invoke when a raw input message was consumed from the buffer. Note that there can only
     * be one listener registered.
     *
     * @param consumeListener the callback to invoke when a raw input message was consumed from the buffer
     */
    public void registerConsumeListener(Consumer<Integer> consumeListener) {
        this.consumeListener = consumeListener;
    }

    /**
     * Registers the callback to invoke when a the event processor determines that a type should be blacklisted.
     * Note that there can only be one listener registered.
     *
     * @param blacklistListener the callback to invoke when a payload type is to be blacklisted.
     */
    public void registerBlacklistListener(Consumer<SerializedType> blacklistListener) {
        this.blacklistListener = blacklistListener;
    }

    @Override
    public Optional<TrackedEventMessage<?>> peek() {
        if (peekEvent == null && eventStream.hasNext()) {
            peekEvent = eventStream.next();
        }
        return Optional.ofNullable(peekEvent);
    }

    @Override
    public boolean hasNextAvailable(int timeout, TimeUnit timeUnit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        try {
            while (peekEvent == null && !eventStream.hasNext() && System.currentTimeMillis() < deadline) {
                if (exception != null) {
                    RuntimeException runtimeException = exception;
                    this.exception = null;
                    throw runtimeException;
                }
                waitForData(deadline);
            }
            return peekEvent != null || eventStream.hasNext();
        } catch (InterruptedException e) {
            logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public TrackedEventMessage<?> nextAvailable() {
        try {
            hasNextAvailable(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
            TrackedEventMessage<?> next = peekEvent == null ? eventStream.next() : peekEvent;
            if (logger.isTraceEnabled()) {
                logger.trace("Polled next available event {} in an Event Buffer {}. Tracking Token {}.",
                             next.getIdentifier(),
                             this,
                             next.trackingToken());
            }
            return next;
        } catch (InterruptedException e) {
            logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
            Thread.currentThread().interrupt();
            return null;
        } finally {
            peekEvent = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        if (closeCallback != null) {
            closeCallback.accept(this);
        }
        events.clear();
    }

    /**
     * Push a new {@code event} on to this EventBuffer instance. Will return {@code false} if the given {@code event}
     * could not be added because the buffer is already closed or if the operation was interrupted. If pushing the
     * {@code event} was successful, {@code true} will be returned.
     *
     * @param event the {@link EventWithToken} to be pushed on to this EventBuffer
     * @return {@code true} if adding the {@code event} to the buffer was successful and {@link false} if it wasn't
     */
    public boolean push(EventWithToken event) {
        if (closed) {
            logger.debug("Received event while closed: {}", event.getToken());
            return false;
        }
        try {
            TrackingToken trackingToken = new GlobalSequenceTrackingToken(event.getToken());
            TrackedDomainEventData<byte[]> trackedDomainEventData =
                    new TrackedDomainEventData<>(trackingToken, new GrpcBackedDomainEventData(event.getEvent()));
            events.put(trackedDomainEventData);
            if (logger.isTraceEnabled()) {
                logger.trace("Pushed event {} in an Event Buffer {}. Tracking Token {}.",
                             trackedDomainEventData.getEventIdentifier(),
                             this,
                             trackingToken);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeCallback.accept(this);
            return false;
        }
        return true;
    }

    /**
     * Fail {@code this} EventBuffer with the given {@link RuntimeException}.
     *
     * @param e a {@link RuntimeException} with which {@code this} EventBuffer failed
     */
    public void fail(RuntimeException e) {
        this.exception = e;
    }

    private static class SimpleSpliterator<T> implements Spliterator<T> {

        private final Supplier<T> supplier;

        protected SimpleSpliterator(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            T nextValue = supplier.get();
            if (nextValue != null) {
                action.accept(nextValue);
            }
            return nextValue != null;
        }

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL | IMMUTABLE;
        }
    }
}

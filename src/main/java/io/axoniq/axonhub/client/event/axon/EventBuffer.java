/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.event.axon;

import io.axoniq.axondb.grpc.EventWithToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.*;
import org.axonframework.serialization.*;
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
    final Logger logger = LoggerFactory.getLogger(EventBuffer.class);

    private final BlockingQueue<TrackedEventData<byte[]>> events;

    private final Iterator<TrackedEventMessage<?>> eventStream;

    private TrackedEventData<byte[]> peekData;
    private TrackedEventMessage<?> peekEvent;
    private Consumer<EventBuffer> closeCallback;
    private RuntimeException exception;
    private Consumer<Integer> consumeListener = i -> {
    };

    /**
     * Initializes an Event Buffer, passing messages through given {@code upcasterChain} and deserializing events using
     * given {@code serializer}.
     *
     * @param upcasterChain The upcasterChain to translate serialized representations before deserializing
     * @param serializer    The serializer capable of deserializing incoming messages
     */
    public EventBuffer(EventUpcaster upcasterChain, Serializer serializer) {
        this.events = new LinkedBlockingQueue<>();
        eventStream = EventUtils.upcastAndDeserializeTrackedEvents(StreamSupport.stream(new SimpleSpliterator<>(this::poll), false),
                                                                   new GrpcMetaDataAwareSerializer(serializer),
                                                                   getOrDefault(upcasterChain, NoOpEventUpcaster.INSTANCE),
                                                                   true)
                                .iterator();
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
            peekData = events.poll(deadline - now, TimeUnit.MILLISECONDS);
            if (peekData != null) {
                consumeListener.accept(1);
            }
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
     * Registers the callback to invoke when a raw input message was consumed from the buffer.
     *
     * @param consumeListener the callback to invoke when a raw input message was consumed from the buffer
     */
    public void registerConsumeListener(Consumer<Integer> consumeListener) {
        this.consumeListener = consumeListener;
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
        if (exception != null) {
            RuntimeException runtimeException = exception;
            this.exception = null;
            throw runtimeException;
        }
        long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        try {
            while (peekEvent == null && !eventStream.hasNext() && System.currentTimeMillis() < deadline) {
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
    public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
        if (exception != null) {
            RuntimeException runtimeException = exception;
            this.exception = null;
            throw runtimeException;
        }
        try {
            hasNextAvailable(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
            return peekEvent == null ? eventStream.next() : peekEvent;
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
        if (closeCallback != null) closeCallback.accept(this);
    }

    public void push(EventWithToken event) {
        try {
            TrackingToken trackingToken = new GlobalSequenceTrackingToken(event.getToken());
            events.put(new TrackedDomainEventData<>(trackingToken, new GrpcBackedDomainEventData(event.getEvent())));
        } catch (Exception e) {
            logger.info(e.getMessage());
            throw new RuntimeException(e);
        }
    }

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

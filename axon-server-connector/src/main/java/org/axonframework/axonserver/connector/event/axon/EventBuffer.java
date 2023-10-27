/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.EventStream;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import org.axonframework.axonserver.connector.AxonServerException;
import org.axonframework.axonserver.connector.ErrorCode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

/**
 * Client-side buffer of messages received from the server. Once consumed from this buffer, the client is notified of a
 * permit being consumed, potentially triggering a permit refresh, if flow control is enabled.
 * <p>
 * This class is intended for internal use. Be cautious.
 *
 * @author Marc Gathier
 * @author Allard Buijze
 * @since 4.0
 */
public class EventBuffer implements TrackingEventStream {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int MAX_AWAIT_AVAILABLE_DATA = 500;

    private final EventStream delegate;
    private final Iterator<TrackedEventMessage<?>> eventStream;
    private final Serializer serializer;
    private final boolean disableIgnoredEventFiltering;

    private TrackedEventMessage<?> peekEvent;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition dataAvailable = lock.newCondition();

    /**
     * Initializes an Event Buffer, passing messages through given {@code upcasterChain} and deserializing events using
     * given {@code serializer}.
     *
     * @param delegate                     the {@link EventStream} to delegate operations to
     * @param upcasterChain                the upcasterChain to translate serialized representations before
     *                                     deserializing
     * @param serializer                   the serializer capable of deserializing incoming messages
     * @param disableIgnoredEventFiltering specifying whether events should or should not be included in the buffer
     */
    public EventBuffer(EventStream delegate,
                       EventUpcaster upcasterChain,
                       Serializer serializer,
                       boolean disableIgnoredEventFiltering) {
        this.delegate = delegate;
        this.serializer = serializer;
        this.disableIgnoredEventFiltering = disableIgnoredEventFiltering;
        this.eventStream = EventUtils.upcastAndDeserializeTrackedEvents(
                StreamSupport.stream(new SimpleSpliterator<>(this::poll), false), serializer, upcasterChain
        ).iterator();

        delegate.onAvailable(() -> {
            lock.lock();
            try {
                dataAvailable.signalAll();
            } finally {
                lock.unlock();
            }
        });
    }

    private TrackedEventData<byte[]> poll() {
        EventWithToken eventWithToken = delegate.nextIfAvailable();
        return eventWithToken == null ? null : convert(eventWithToken);
    }

    private TrackedEventData<byte[]> convert(EventWithToken eventWithToken) {
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(eventWithToken.getToken());
        return new TrackedDomainEventData<>(trackingToken, new GrpcBackedDomainEventData(eventWithToken.getEvent()));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation removes events from the stream based on the payload type of the given message.
     */
    @Override
    public void skipMessagesWithPayloadTypeOf(TrackedEventMessage<?> ignoredMessage) {
        if (!disableIgnoredEventFiltering) {
            SerializedType serializedType;
            if (UnknownSerializedType.class.equals(ignoredMessage.getPayloadType())) {
                UnknownSerializedType unknownSerializedType = (UnknownSerializedType) ignoredMessage.getPayload();
                serializedType = unknownSerializedType.serializedType();
                delegate.excludePayloadType(serializedType.getName(), serializedType.getRevision());
            } else {
                serializedType = serializer.typeForClass(ignoredMessage.getPayloadType());
            }
            delegate.excludePayloadType(serializedType.getName(), serializedType.getRevision());
        }
    }

    @Override
    public Optional<TrackedEventMessage<?>> peek() {
        return Optional.ofNullable(peekNullable());
    }

    @Override
    public boolean hasNextAvailable(int timeout, TimeUnit timeUnit) {
        long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        try {
            while (peekNullable() == null && System.currentTimeMillis() < deadline) {
                lock.lock();
                try {
                    long waitTime = deadline - System.currentTimeMillis();
                    // Check if an event has arrived in the meantime and if wait time greater than zero.
                    // Only then is it worth waiting.
                    if (peekNullable() == null && waitTime > 0) {
                        boolean await =
                                dataAvailable.await(Math.min(waitTime, MAX_AWAIT_AVAILABLE_DATA),
                                                    TimeUnit.MILLISECONDS);
                        logger.trace(await ? "Signaled new events are available"
                                             : "No signal received for new events, exiting await");
                    }
                } finally {
                    lock.unlock();
                }
            }

            return peekNullable() != null;
        } catch (InterruptedException e) {
            logger.warn("Event consumer thread was interrupted. Returning thread to event processor.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private TrackedEventMessage<?> peekNullable() {
        if (peekEvent == null && eventStream.hasNext()) {
            peekEvent = eventStream.next();
        }
        // If the peeked event still is null, the EventStream might've been closed.
        if (peekEvent == null && delegate.isClosed()) {
            throw new AxonServerException(ErrorCode.OTHER.errorCode(),
                                          "The Event Stream has been closed, so no further events can be retrieved",
                                          delegate.getError().orElse(null));
        }
        return peekEvent;
    }

    @Override
    public TrackedEventMessage<?> nextAvailable() {
        try {
            hasNextAvailable(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
            return peekEvent == null ? eventStream.next() : peekEvent;
        } finally {
            peekEvent = null;
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean setOnAvailableCallback(Runnable callback) {
        delegate.onAvailable(callback);
        return true;
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

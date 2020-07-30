/*
 * Copyright (c) 2010-2020. Axon Framework
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static org.axonframework.common.ObjectUtils.getOrDefault;

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

    private static final Logger logger = LoggerFactory.getLogger(EventBuffer.class);

    private static final int DEFAULT_POLLING_TIME_MILLIS = 500;

    private final Serializer serializer;
    private final EventStream delegate;
    private final boolean disableEventBlacklisting;
    private final Iterator<TrackedEventMessage<?>> eventStream;

    private TrackedEventMessage<?> peekEvent;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition dataAvailable = lock.newCondition();

    /**
     * Initializes an Event Buffer, passing messages through given {@code upcasterChain} and deserializing events using
     * given {@code serializer}.
     *
     * @param delegate                 the {@link EventStream} to delegate operations to
     * @param upcasterChain            the upcasterChain to translate serialized representations before deserializing
     * @param serializer               the serializer capable of deserializing incoming messages
     * @param disableEventBlacklisting specifying whether events should or should not be included in the buffer
     */
    public EventBuffer(EventStream delegate,
                       EventUpcaster upcasterChain,
                       Serializer serializer,
                       boolean disableEventBlacklisting) {
        this.serializer = serializer;
        this.delegate = delegate;
        this.disableEventBlacklisting = disableEventBlacklisting;

        this.eventStream = EventUtils.upcastAndDeserializeTrackedEvents(
                StreamSupport.stream(new SimpleSpliterator<>(this::poll), false),
                new GrpcMetaDataAwareSerializer(serializer),
                getOrDefault(upcasterChain, NoOpEventUpcaster.INSTANCE)
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
        if (eventWithToken == null) {
            return null;
        }
        return convert(eventWithToken);
    }

    private TrackedEventData<byte[]> convert(EventWithToken eventWithToken) {
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(eventWithToken.getToken());
        return new TrackedDomainEventData<>(trackingToken, new GrpcBackedDomainEventData(eventWithToken.getEvent()));
    }

    /**
     * {@inheritDoc} ----
     * <p>
     * This implementation removes events from the stream based on the payload type of the given message.
     */
    @Override
    public void blacklist(TrackedEventMessage<?> trackedEventMessage) {
        if (!disableEventBlacklisting) {
            SerializedType serializedType;
            if (UnknownSerializedType.class.equals(trackedEventMessage.getPayloadType())) {
                UnknownSerializedType unknownSerializedType = (UnknownSerializedType) trackedEventMessage.getPayload();
                serializedType = unknownSerializedType.serializedType();
                delegate.excludePayloadType(serializedType.getName(), serializedType.getRevision());
            } else {
                serializedType = serializer.typeForClass(trackedEventMessage.getPayloadType());
            }
            delegate.excludePayloadType(serializedType.getName(), serializedType.getRevision());
        }
    }

    @Override
    public Optional<TrackedEventMessage<?>> peek() {
        if (peekEvent == null && eventStream.hasNext()) {
            peekEvent = eventStream.next();
        }
        return Optional.ofNullable(peekEvent);
    }

    @Override
    public boolean hasNextAvailable(int timeout, TimeUnit timeUnit) {
        long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        try {
            do {
                long waitTime = deadline - System.currentTimeMillis();
                waitForData(waitTime);
            } while (peekEvent == null && System.currentTimeMillis() < deadline && !eventStream.hasNext());

            return peekEvent != null || eventStream.hasNext();
        } catch (InterruptedException e) {
            logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void waitForData(long timeout) throws InterruptedException {
        // a quick check before acquiring the lock
        if (delegate.peek() != null) {
            return;
        }
        if (timeout > 0) {
            lock.lock();
            try {
                // check again for concurrency reasons
                if (delegate.peek() == null) {
                    dataAvailable.await(Math.min(DEFAULT_POLLING_TIME_MILLIS, timeout), TimeUnit.MILLISECONDS);
                }
            } finally {
                lock.unlock();
            }
        }
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

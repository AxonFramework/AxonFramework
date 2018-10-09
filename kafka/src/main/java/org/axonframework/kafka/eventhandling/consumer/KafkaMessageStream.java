/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Create message stream from a specific kafka topic. Messages are fetch in bulk and stored in an in-memory buffer. We
 * try to introduce some sort and stored them in a local buffer. Consumer position is tracked via
 * {@link KafkaTrackingToken}. Records are fetched from kafka and stored in-memory buffer.
 * <p>
 * This is not thread safe.
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 * @since 3.0
 */
public class KafkaMessageStream implements TrackingEventStream {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageStream.class);

    private final Buffer<KafkaEventMessage> buffer;
    private final Runnable closeHandler;
    private KafkaEventMessage peekEvent;

    public KafkaMessageStream(Buffer<KafkaEventMessage> buffer, Runnable closeHandler) {
        Assert.notNull(buffer, () -> "Buffer may not be null");
        this.closeHandler = closeHandler;
        this.buffer = buffer;
    }


    @Override
    public Optional<TrackedEventMessage<?>> peek() {
        return Optional.ofNullable(
                peekEvent == null && !hasNextAvailable(0, TimeUnit.NANOSECONDS) ? null : peekEvent.value());
    }

    @Override
    public boolean hasNextAvailable(int timeout, TimeUnit unit) {
        try {
            return peekEvent != null || (peekEvent = buffer.poll(timeout, unit)) != null;
        } catch (InterruptedException e) {
            logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public TrackedEventMessage<?> nextAvailable() {
        try {
            return peekEvent == null ? buffer.take().value() : peekEvent.value();
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
        closeHandler.run();
    }
}

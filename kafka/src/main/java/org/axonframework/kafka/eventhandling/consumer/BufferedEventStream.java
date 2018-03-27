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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Nakul Mishra
 * @since 3.0
 */
class BufferedEventStream {

    private static final Logger logger = LoggerFactory.getLogger(BufferedEventStream.class);
    private final MessageBuffer<MessageAndMetadata> buffer;
    private MessageAndMetadata peekEvent;

    BufferedEventStream(MessageBuffer<MessageAndMetadata> buffer) {
        Assert.isTrue(buffer != null, () -> "Buffer cannot be null");
        this.buffer = buffer;
    }

    Optional<TrackedEventMessage<?>> peek() {
        return Optional.ofNullable(
                peekEvent == null && !hasNextAvailable(0, TimeUnit.NANOSECONDS) ? null : peekEvent.value());
    }

    boolean hasNextAvailable(int timeout, TimeUnit unit) {
        try {
            return peekEvent != null || (peekEvent = buffer.poll(timeout, unit)) != null;
        } catch (InterruptedException e) {
            logger.warn("Consumer thread was interrupted. Returning thread to event processor.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    TrackedEventMessage<?> nextAvailable() {
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
}
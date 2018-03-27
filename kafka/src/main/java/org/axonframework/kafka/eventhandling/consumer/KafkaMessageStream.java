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

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Create message stream from a specific kafka topic. Messages are fetch in bulk and stored in an in-memory buffer. We
 * try
 * to introduce some sort
 * and stored them in a local buffer.
 * Consumer position is tracked via {@link KafkaTrackingToken}Records are fetched from kafka and stored in-memory
 * buffer.
 * <p>
 * This is not thread safe
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 * @since 3.0
 */
public class KafkaMessageStream implements TrackingEventStream {

    private final BufferedEventStream eventStream;
    private final Fetcher fetcher;

    public KafkaMessageStream(MessageBuffer<MessageAndMetadata> buffer,
                              Fetcher fetcher) {
        Assert.notNull(buffer, () -> "Buffer may not be null");
        Assert.notNull(fetcher, () -> "Fetcher may not be null");
        this.eventStream = new BufferedEventStream(buffer);
        this.fetcher = fetcher;
    }

    @Override
    public Optional<TrackedEventMessage<?>> peek() {
        return eventStream.peek();
    }

    @Override
    public boolean hasNextAvailable(int timeout, TimeUnit unit) {
        return eventStream.hasNextAvailable(timeout, unit);
    }

    @Override
    public TrackedEventMessage<?> nextAvailable() {
        return eventStream.nextAvailable();
    }

    @Override
    public void close() {
        fetcher.shutdown();
    }
}

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

import org.apache.kafka.clients.consumer.Consumer;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
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
public class KafkaMessageStream<K, V> implements TrackingEventStream {
    private final BufferedEventStream<K, V> eventConsumer;
    private final ExecutorService fetcher;

    public KafkaMessageStream(Consumer<K, V> consumer, BlockingQueue<MessageAndTimestamp> buffer,
                              ExecutorService fetcher) {
        this.eventConsumer = new BufferedEventStream<>(consumer, buffer);
        this.fetcher = fetcher;
    }

    @Override
    public Optional<TrackedEventMessage<?>> peek() {
        return eventConsumer.peek();
    }

    @Override
    public boolean hasNextAvailable(int timeout, TimeUnit unit) {
        return eventConsumer.hasNextAvailable(timeout, unit);
    }

    @Override
    public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
        return eventConsumer.nextAvailable();
    }

    @Override
    public void close() {
        eventConsumer.close();
        this.fetcher.shutdownNow();
    }
}

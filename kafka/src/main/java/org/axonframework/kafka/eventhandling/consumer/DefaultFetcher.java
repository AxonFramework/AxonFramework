/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.MessageStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Executes {@link FetchEventsTask}.
 *
 * @author Nakul Mishra
 */
public class DefaultFetcher<K, V> implements Fetcher<K, V> {

    private final SortableBuffer<MessageAndMetadata> buffer;
    private final ExecutorService pool;
    private final KafkaMessageConverter<K, V> converter;
    private final Consumer<K, V> consumer;
    private final String topic;
    private final BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback;
    private final long pollTimeout;

    private Future<?> currentTask;

    private DefaultFetcher(Builder<K, V> builder) {
        this.buffer = builder.buffer;
        this.consumer = builder.consumerFactory.createConsumer();
        this.converter = builder.converter;
        this.topic = builder.topic;
        this.pool = builder.pool;
        this.callback = builder.callback;
        this.pollTimeout = builder.pollTimeout;
    }

    @Override
    public MessageStream<TrackedEventMessage<?>> start(KafkaTrackingToken token) {
        ConsumerUtil.seek(topic, consumer, token);
        if (KafkaTrackingToken.isEmpty(token)) {
            token = KafkaTrackingToken.emptyToken();
        }
        currentTask = pool.submit(FetchEventsTask.builder(this.consumer,
                                                          token,
                                                          this.buffer,
                                                          this.converter,
                                                          this.callback,
                                                          this.pollTimeout).build());
        return new KafkaMessageStream(buffer, this);
    }

    @Override
    public void shutdown() {
        currentTask.cancel(true);
        pool.shutdown();
        buffer.clear();
    }

    /**
     * @param <K> key type.
     * @param <V> value type.
     * @return the builder.
     */
    public static <K, V> Builder<K, V> builder(String topic, ConsumerFactory<K, V> factory,
                                               KafkaMessageConverter<K, V> converter,
                                               SortableBuffer<MessageAndMetadata> buffer, long pollTimeout) {
        return new Builder<>(topic, factory, converter, buffer, pollTimeout);
    }

    /**
     * @param <K> key type.
     * @param <V> value type.
     */
    public static final class Builder<K, V> {

        private final SortableBuffer<MessageAndMetadata> buffer;
        private final KafkaMessageConverter<K, V> converter;
        private final String topic;
        private final ConsumerFactory<K, V> consumerFactory;
        private final long pollTimeout;

        private ExecutorService pool = newSingleThreadExecutor(new ThreadFactory() {
            private AtomicLong counter = new AtomicLong(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(false);
                thread.setName("DefaultFetcher-pool-thread-" + counter.getAndIncrement());
                return thread;
            }
        });
        private BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback = emptyCallback();

        private Builder(String topic, ConsumerFactory<K, V> factory, KafkaMessageConverter<K, V> converter,
                        SortableBuffer<MessageAndMetadata> buffer, long pollTimeout) {
            Assert.notNull(topic, () -> "Topic may not be null");
            Assert.notNull(factory, () -> "Factory may not be null");
            Assert.notNull(converter, () -> "Converter may not be null");
            Assert.notNull(buffer, () -> "Buffer may not be null");
            Assert.isFalse(pollTimeout < 0, () -> "PollTimeout may not be < 0");
            this.topic = topic;
            this.consumerFactory = factory;
            this.converter = converter;
            this.buffer = buffer;
            this.pollTimeout = pollTimeout;
        }

        /**
         * Configure {@link ExecutorService} that uses {@link Consumer} for fetching Kafka records.
         *
         * @param sevice ExecutorService.
         * @return the builder.
         */
        public Builder<K, V> withPool(ExecutorService sevice) {
            Assert.notNull(sevice, () -> "Pool may not be null");
            this.pool = sevice;
            return this;
        }

        /**
         * Configure {@link Function} to invoke once a {@link ConsumerRecord} is inserted into the {@link
         * SortableBuffer}.
         *
         * @param callback function type.
         * @return the builder.
         */
        public Builder<K, V> onRecordPublished(
                BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback) {
            Assert.notNull(callback, () -> "Callback may not be null");
            this.callback = callback;
            return this;
        }

        /**
         * {@link Function} that does nothing oonce a {@link ConsumerRecord} is inserted into the {@link
         * SortableBuffer}.
         *
         * @param <K> key type.
         * @param <V> value type.
         * @return the function.
         */
        public static <K, V> BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> emptyCallback() {
            return (r, t) -> null;
        }

        public Fetcher<K, V> build() {
            return new DefaultFetcher<>(this);
        }
    }
}
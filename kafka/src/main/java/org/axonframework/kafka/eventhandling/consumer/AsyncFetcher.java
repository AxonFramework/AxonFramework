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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * Async implementation of the {@link Fetcher} that uses an in-memory bufferFactory.
 *
 * @param <K> The key of the Kafka entries
 * @param <V> The value type of Kafka entries
 */
public class AsyncFetcher<K, V> implements Fetcher {

    private final Supplier<Buffer<KafkaEventMessage>> bufferFactory;
    private final ExecutorService pool;
    private final KafkaMessageConverter<K, V> converter;
    private final ConsumerFactory<K, V> consumerFactory;
    private final String topic;
    private final BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback;
    private final long pollTimeout;
    private final boolean requirePoolShutdown;
    private final Set<FetchEventsTask> activeFetchers = ConcurrentHashMap.newKeySet();

    private AsyncFetcher(Builder<K, V> builder) {
        this.bufferFactory = builder.bufferFactory;
        this.consumerFactory = builder.consumerFactory;
        this.converter = builder.converter;
        this.topic = builder.topic;
        this.requirePoolShutdown = builder.requirePoolShutdown;
        this.pool = builder.pool;
        this.callback = builder.callback;
        this.pollTimeout = builder.pollTimeout;
    }

    /**
     * Initialize a builder for configuring an AsyncFetcher, using given Kafka {@code consumerConfig}.
     * <p>
     * Note that configuring a MessageConverter on the builder is mandatory if the value type is not {@code byte[]}.
     *
     * @param <K>            key type.
     * @param <V>            value type.
     * @param consumerConfig The configuration of KafkaConsumers to use when creating consumers
     * @return the builder.
     */
    public static <K, V> Builder<K, V> builder(Map<String, Object> consumerConfig) {
        return builder(new DefaultConsumerFactory<>(consumerConfig));
    }

    /**
     * Initialize a builder for configuring an AsyncFetcher, using given {@code consumerFactory} for creating Kafka
     * Consumers.
     * <p>
     * Note that configuring a MessageConverter on the builder is mandatory if the value type is not {@code byte[]}.
     *
     * @param <K>             key type.
     * @param <V>             value type.
     * @param consumerFactory The factory providing Kafka Consumer instances
     * @return the builder.
     */
    public static <K, V> Builder<K, V> builder(ConsumerFactory<K, V> consumerFactory) {
        return new Builder<>(consumerFactory);
    }

    @Override
    public BlockingStream<TrackedEventMessage<?>> start(KafkaTrackingToken token) {
        Consumer<K, V> consumer = consumerFactory.createConsumer();
        ConsumerUtil.seek(topic, consumer, token);
        if (KafkaTrackingToken.isEmpty(token)) {
            token = KafkaTrackingToken.emptyToken();
        }
        Buffer<KafkaEventMessage> buffer = bufferFactory.get();
        FetchEventsTask<K, V> fetcherTask = new FetchEventsTask<>(consumer, token, buffer, this.converter,
                                                                  this.callback, this.pollTimeout,
                                                                  activeFetchers::remove);
        activeFetchers.add(fetcherTask);
        pool.execute(fetcherTask);

        return new KafkaMessageStream(buffer, fetcherTask::close);
    }

    @Override
    public void shutdown() {
        activeFetchers.forEach(FetchEventsTask::close);
        if (requirePoolShutdown) {
            pool.shutdown();
        }
    }

    /**
     * Builder for the AsyncFetcher.
     *
     * @param <K> key type.
     * @param <V> value type.
     */
    public static final class Builder<K, V> {

        private final ConsumerFactory<K, V> consumerFactory;
        private Supplier<Buffer<KafkaEventMessage>> bufferFactory = SortedKafkaMessageBuffer::new;
        private KafkaMessageConverter<K, V> converter =
                (KafkaMessageConverter<K, V>) new DefaultKafkaMessageConverter(new XStreamSerializer());
        private String topic = "Axon.Events";
        private long pollTimeout = 5_000;

        private ExecutorService pool = newCachedThreadPool(new AxonThreadFactory("AsyncFetcher-pool-thread"));
        private BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> callback = (r, t) -> null;
        private boolean requirePoolShutdown = true;

        private Builder(ConsumerFactory<K, V> consumerFactory) {
            Assert.notNull(consumerFactory, () -> "ConsumerFactory may not be null");
            this.consumerFactory = consumerFactory;
        }

        /**
         * Configure {@link ExecutorService} that uses {@link Consumer} for fetching Kafka records. Note that the pool
         * should contain sufficient threads to run the necessary fetcher processes concurrently.
         * <p>
         * Note that the provided pool will <em>not</em> be shut down when the fetcher is terminated.
         *
         * @param sevice ExecutorService.
         * @return the builder.
         */
        public Builder<K, V> withPool(ExecutorService sevice) {
            Assert.notNull(sevice, () -> "Pool may not be null");
            this.requirePoolShutdown = false;
            this.pool = sevice;
            return this;
        }

        /**
         * Configure {@link Function} to invoke once a {@link ConsumerRecord} is inserted into the {@link
         * Buffer}.
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
         * Configure {@link ExecutorService} that uses {@link Consumer} for fetching Kafka records.
         *
         * @param timeout the timeout when reading message from the topic.
         * @param unit    the unit in which the timeout is expressed.
         * @return this builder for method chaining.
         */
        public Builder<K, V> withPollTimeout(long timeout, TimeUnit unit) {
            this.pollTimeout = unit.toMillis(timeout);
            return this;
        }

        /**
         * Configure the converter which converts Kafka messages to Axon messages.
         *
         * @param converter the converter.
         * @return this builder for method chaining.
         */
        public Builder<K, V> withMessageConverter(KafkaMessageConverter<K, V> converter) {
            Assert.notNull(converter, () -> "Converter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Configure Kafka topic to read events from.
         *
         * @param topic the topic.
         * @return this builder for method chaining.
         */
        public Builder<K, V> withTopic(String topic) {
            Assert.notNull(topic, () -> "Topic may not be null");
            this.topic = topic;
            return this;
        }

        /**
         * Configure the factory for creating buffer that is used for each connection.
         *
         * @param bufferFactory the bufferFactory.
         * @return this builder for method chaining.
         */
        public Builder<K, V> withBufferFactory(Supplier<Buffer<KafkaEventMessage>> bufferFactory) {
            Assert.notNull(bufferFactory, () -> "Buffer factory may not be null");
            this.bufferFactory = bufferFactory;
            return this;
        }

        /**
         * Builds the fetcher
         *
         * @return the Fetcher
         */
        public Fetcher build() {
            return new AsyncFetcher<>(this);
        }
    }
}

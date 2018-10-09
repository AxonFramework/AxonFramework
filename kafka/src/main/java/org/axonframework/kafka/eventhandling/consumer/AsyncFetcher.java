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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Async implementation of the {@link Fetcher} that uses an in-memory bufferFactory.
 *
 * @param <K> the key of the Kafka entries
 * @param <V> the value type of Kafka entries
 * @author Nakul Mishra
 * @since 3.3
 */
public class AsyncFetcher<K, V> implements Fetcher {

    private final ConsumerFactory<K, V> consumerFactory;
    private final Supplier<Buffer<KafkaEventMessage>> bufferFactory;
    private final ExecutorService executorService;
    private final boolean requirePoolShutdown;
    private final KafkaMessageConverter<K, V> messageConverter;
    private final String topic;
    private final BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> consumerRecordCallback;
    private final long pollTimeout;

    private final Set<FetchEventsTask> activeFetchers = ConcurrentHashMap.newKeySet();

    /**
     * Instantiate a {@link AsyncFetcher} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link ConsumerFactory} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AsyncFetcher} instance
     */
    protected AsyncFetcher(Builder<K, V> builder) {
        builder.validate();
        this.consumerFactory = builder.consumerFactory;
        this.bufferFactory = builder.bufferFactory;
        this.executorService = builder.executorService;
        this.requirePoolShutdown = builder.requirePoolShutdown;
        this.messageConverter = builder.messageConverter;
        this.topic = builder.topic;
        this.consumerRecordCallback = builder.consumerRecordCallback;
        this.pollTimeout = builder.pollTimeout;
    }

    /**
     * Instantiate a Builder to be able to create a {@link AsyncFetcher}.
     * <p>
     * The {@code bufferFactory} is defaulted to a {@link SortedKafkaMessageBuffer}, the {@link ExecutorService} to an
     * {@link Executors#newCachedThreadPool()} using an {@link AxonThreadFactory}, the {@link KafkaMessageConverter} to
     * a {@link DefaultKafkaMessageConverter}, the {@code topic} to {@code Axon.Events}, the
     * {@code consumerRecordCallback} to a no-op function and the {@code pollTimeout} to {@code 5000} milliseconds.
     * The {@link ConsumerFactory} is a <b>hard requirements</b> and as such should be provided.
     *
     * @param <K> a generic type for the key of the {@link ConsumerFactory}, {@link ConsumerRecord} and {@link
     *            KafkaMessageConverter}
     * @param <V> a generic type for the value of the {@link ConsumerFactory}, {@link ConsumerRecord} and {@link
     *            KafkaMessageConverter}
     * @return a Builder to be able to create a {@link []}
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    @Override
    public BlockingStream<TrackedEventMessage<?>> start(KafkaTrackingToken token) {
        Consumer<K, V> consumer = consumerFactory.createConsumer();
        ConsumerUtil.seek(topic, consumer, token);
        if (KafkaTrackingToken.isEmpty(token)) {
            token = KafkaTrackingToken.emptyToken();
        }
        Buffer<KafkaEventMessage> buffer = bufferFactory.get();
        FetchEventsTask<K, V> fetcherTask = new FetchEventsTask<>(
                consumer, token, buffer, this.messageConverter, this.consumerRecordCallback, this.pollTimeout,
                activeFetchers::remove
        );
        activeFetchers.add(fetcherTask);
        executorService.execute(fetcherTask);

        return new KafkaMessageStream(buffer, fetcherTask::close);
    }

    @Override
    public void shutdown() {
        activeFetchers.forEach(FetchEventsTask::close);
        if (requirePoolShutdown) {
            executorService.shutdown();
        }
    }

    /**
     * Builder class to instantiate an {@link AsyncFetcher}.
     * <p>
     * The {@code bufferFactory} is defaulted to an {@link SortedKafkaMessageBuffer}, the {@link ExecutorService} to an
     * {@link Executors#newCachedThreadPool()} using an {@link AxonThreadFactory}, the {@link KafkaMessageConverter} to
     * a {@link DefaultKafkaMessageConverter}, the {@code topic} to {@code Axon.Events}, the
     * {@code consumerRecordCallback} to a no-op function and the {@code pollTimeout} to {@code 5000} milliseconds.
     * The {@link ConsumerFactory} is a <b>hard requirements</b> and as such should be provided.
     *
     * @param <K> a generic type for the key of the {@link ConsumerFactory}, {@link ConsumerRecord} and {@link
     *            KafkaMessageConverter}
     * @param <V> a generic type for the value of the {@link ConsumerFactory}, {@link ConsumerRecord} and {@link
     *            KafkaMessageConverter}
     */
    public static final class Builder<K, V> {

        private ConsumerFactory<K, V> consumerFactory;
        private Supplier<Buffer<KafkaEventMessage>> bufferFactory = SortedKafkaMessageBuffer::new;
        private ExecutorService executorService =
                Executors.newCachedThreadPool(new AxonThreadFactory("AsyncFetcher-pool-thread"));
        private boolean requirePoolShutdown = true;
        @SuppressWarnings("unchecked")
        private KafkaMessageConverter<K, V> messageConverter =
                (KafkaMessageConverter<K, V>) DefaultKafkaMessageConverter.builder()
                                                                          .serializer(XStreamSerializer.builder()
                                                                                                       .build())
                                                                          .build();
        private String topic = "Axon.Events";
        private BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> consumerRecordCallback = (r, t) -> null;
        private long pollTimeout = 5_000;


        /**
         * Sets the {@link ConsumerFactory} to be used by this {@link Fetcher} implementation to create {@link Consumer}
         * instances.
         *
         * @param consumerFactory a {@link ConsumerFactory} to be used by this {@link Fetcher} implementation to create
         *                        {@link Consumer} instances
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> consumerFactory(ConsumerFactory<K, V> consumerFactory) {
            assertNonNull(consumerFactory, "ConsumerFactory may not be null");
            this.consumerFactory = consumerFactory;
            return this;
        }

        /**
         * Instantiate a {@link DefaultConsumerFactory} with the provided {@code consumerConfiguration}. Used by this
         * {@link Fetcher} implementation to create {@link Consumer} instances.
         *
         * @param consumerConfiguration a {@link DefaultConsumerFactory} with the given {@code consumerConfiguration},
         *                              to be used by this {@link Fetcher} implementation to create {@link Consumer}
         *                              instances
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> consumerFactory(Map<String, Object> consumerConfiguration) {
            this.consumerFactory = new DefaultConsumerFactory<>(consumerConfiguration);
            return this;
        }

        /**
         * Sets the {@link ExecutorService} used to start {@link Consumer} instances for fetching Kafka records.
         * Note that the executorService should contain sufficient threads to run the necessary fetcher processes
         * concurrently. Defaults to an {@link Executors#newCachedThreadPool()} with an {@link AxonThreadFactory}.
         * <p>
         * Note that the provided executorService will <em>not</em> be shut down when the fetcher is terminated.
         *
         * @param executorService a {@link ExecutorService} used to start {@link Consumer} instances for fetching Kafka
         *                        records
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> executorService(ExecutorService executorService) {
            assertNonNull(executorService, "ExecutorService may not be null");
            this.requirePoolShutdown = false;
            this.executorService = executorService;
            return this;
        }

        /**
         * Sets the {@code bufferFactory} of type {@link Supplier} with a generic type {@link Buffer} with
         * {@link KafkaEventMessage}s. Used to create a buffer for the Kafka records fetcher. Defaults to a
         * {@link SortedKafkaMessageBuffer}.
         *
         * @param bufferFactory a {@link Supplier} to create a buffer for the Kafka records fetcher
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> bufferFactory(Supplier<Buffer<KafkaEventMessage>> bufferFactory) {
            assertNonNull(bufferFactory, "Buffer factory may not be null");
            this.bufferFactory = bufferFactory;
            return this;
        }

        /**
         * Sets the {@link KafkaMessageConverter} used to convert Kafka messages into
         * {@link org.axonframework.eventhandling.EventMessage}s. Defaults to a {@link DefaultKafkaMessageConverter}
         * using the {@link XStreamSerializer}.
         * <p>
         * Note that configuring a MessageConverter on the builder is mandatory if the value type is not {@code byte[]}.
         *
         * @param messageConverter a {@link KafkaMessageConverter} used to convert Kafka messages into
         *                         {@link org.axonframework.eventhandling.EventMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> messageConverter(KafkaMessageConverter<K, V> messageConverter) {
            assertNonNull(messageConverter, "MessageConverter may not be null");
            this.messageConverter = messageConverter;
            return this;
        }

        /**
         * Set the Kafka {@code topic} to read {@link org.axonframework.eventhandling.EventMessage}s from. Defaults to
         * {@code Axon.Events}.
         *
         * @param topic the Kafka {@code topic} to read {@link org.axonframework.eventhandling.EventMessage}s from
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> topic(String topic) {
            assertThat(topic, name -> Objects.nonNull(name) && !"".equals(name), "The topic may not be null or empty");
            this.topic = topic;
            return this;
        }

        /**
         * Sets the {@code consumerRecordCallback} {@link BiFunction} used to invoke once a {@link ConsumerRecord} is
         * inserted into the {@link Buffer}. Defaults to a no-op function.
         *
         * @param consumerRecordCallback a {@code consumerRecordCallback} {@link BiFunction} used to invoke once a
         *                               {@link ConsumerRecord} is inserted into the {@link Buffer}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> consumerRecordCallback(
                BiFunction<ConsumerRecord<K, V>, KafkaTrackingToken, Void> consumerRecordCallback) {
            assertNonNull(consumerRecordCallback, "The consumerRecordCallback may not be null");
            this.consumerRecordCallback = consumerRecordCallback;
            return this;
        }

        /**
         * Set the {@code pollTimeout} in milliseconds for reading messages from a topic. Defaults to {@code 5000}
         * milliseconds.
         *
         * @param timeout  the timeout as a {@code long} when reading message from the topic
         * @param timeUnit the {@link TimeUnit} in which the timeout is expressed
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> pollTimeout(long timeout, TimeUnit timeUnit) {
            this.pollTimeout = timeUnit.toMillis(timeout);
            return this;
        }

        /**
         * Initializes a {@link AsyncFetcher} as specified through this Builder.
         *
         * @return a {@link AsyncFetcher} as specified through this Builder
         */
        public AsyncFetcher build() {
            return new AsyncFetcher<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(consumerFactory, "The ConsumerFactory is a hard requirement and should be provided");
        }
    }
}

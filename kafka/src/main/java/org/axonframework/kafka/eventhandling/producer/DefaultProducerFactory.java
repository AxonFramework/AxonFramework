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

package org.axonframework.kafka.eventhandling.producer;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.axonframework.common.AxonConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * The {@link ProducerFactory} implementation to produce a {@code singleton} shared {@link Producer} instance.
 * <p>
 * The {@link Producer} instance is freed from the external {@link Producer#close()} invocation with the internal
 * wrapper. The real {@link Producer#close()} is called on the target {@link Producer} during the {@link #shutDown()}.
 * <p>
 * Setting {@link Builder#confirmationMode(ConfirmationMode)} to transactional produces a transactional producer; in
 * which case, a cache of producers is maintained; closing the producer returns it to the cache. If cache is full the
 * producer will be closed {@link KafkaProducer#close(long, TimeUnit)} and evicted from cache.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public class DefaultProducerFactory<K, V> implements ProducerFactory<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProducerFactory.class);

    private final int closeTimeout;
    private final TimeUnit timeUnit;
    private final BlockingQueue<CloseLazyProducer<K, V>> cache;
    private final Map<String, Object> configuration;
    private final ConfirmationMode confirmationMode;
    private final String transactionIdPrefix;

    private final AtomicInteger transactionIdSuffix;

    private volatile CloseLazyProducer<K, V> producer;

    /**
     * Instantiate a {@link DefaultProducerFactory} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@code configuration} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link []} instance
     */
    protected DefaultProducerFactory(Builder<K, V> builder) {
        builder.validate();
        this.closeTimeout = builder.closeTimeout;
        this.timeUnit = builder.timeUnit;
        this.cache = new ArrayBlockingQueue<>(builder.producerCacheSize);
        this.configuration = builder.configuration;
        this.confirmationMode = builder.confirmationMode;
        this.transactionIdPrefix = builder.transactionIdPrefix;
        this.transactionIdSuffix = new AtomicInteger();
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultProducerFactory}.
     * <p>
     * The {@code closeTimeout} is defaulted to {@code 30}, the {@link TimeUnit} for the {@code closeTimeout} is
     * defaulted to {@link TimeUnit#SECONDS}, the {@code producerCacheSize} defaults to {@code 10} and the
     * {@link ConfirmationMode} is defaulted to {@link ConfirmationMode#NONE}.
     * The {@code configuration} is a <b>hard requirement</b> and as such should be provided.
     *
     * @param <K> a generic type for the key of the {@link Producer} this {@link ProducerFactory} will create
     * @param <V> a generic type for the value of the {@link Producer} this {@link ProducerFactory} will create
     * @return a Builder to be able to create a {@link DefaultProducerFactory}
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Create a producer with the settings supplied in configuration properties.
     *
     * @return the producer.
     */
    @Override
    public Producer<K, V> createProducer() {
        if (confirmationMode.isTransactional()) {
            return createTransactionalProducer();
        }
        if (this.producer == null) {
            synchronized (this) {
                if (this.producer == null) {
                    this.producer = new CloseLazyProducer<>(createKafkaProducer(configuration),
                                                            cache,
                                                            closeTimeout,
                                                            timeUnit);
                }
            }
        }
        return this.producer;
    }

    /**
     * Confirmation mode for all producer instances.
     *
     * @return the confirmation mode.
     */
    @Override
    public ConfirmationMode confirmationMode() {
        return confirmationMode;
    }

    /**
     * Return an unmodifiable reference to the configuration map for this factory.
     * Useful for cloning to make a similar factory.
     *
     * @return the configuration.
     */
    public Map<String, Object> configurationProperties() {
        return Collections.unmodifiableMap(configuration);
    }

    /**
     * TransactionalIdPrefix for all producer instances.
     *
     * @return the transactionalIdPrefix.
     */
    public String transactionIdPrefix() {
        return transactionIdPrefix;
    }

    /**
     * Closes all producer instances.
     */
    @Override
    public void shutDown() {
        CloseLazyProducer<K, V> producer = this.producer;
        this.producer = null;
        if (producer != null) {
            producer.delegate.close(this.closeTimeout, timeUnit);
        }
        producer = this.cache.poll();
        while (producer != null) {
            try {
                producer.delegate.close(this.closeTimeout, timeUnit);
            } catch (Exception e) {
                logger.error("Exception closing producer", e);
            }
            producer = this.cache.poll();
        }
    }

    private Producer<K, V> createTransactionalProducer() {
        Producer<K, V> producer = this.cache.poll();
        if (producer != null) {
            return producer;
        }
        Map<String, Object> configs = new HashMap<>(this.configuration);
        configs.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,
                    this.transactionIdPrefix + this.transactionIdSuffix.getAndIncrement());
        producer = new CloseLazyProducer<>(createKafkaProducer(configs), cache, closeTimeout, timeUnit);
        producer.initTransactions();
        return producer;
    }

    private Producer<K, V> createKafkaProducer(Map<String, Object> configs) {
        return new KafkaProducer<>(configs);
    }

    private static final class CloseLazyProducer<K, V> implements Producer<K, V> {

        private final Producer<K, V> delegate;
        private final BlockingQueue<CloseLazyProducer<K, V>> cache;
        private final int closeTimeout;
        private final TimeUnit unit;

        CloseLazyProducer(Producer<K, V> delegate, BlockingQueue<CloseLazyProducer<K, V>> cache, int closeTimeout,
                          TimeUnit unit) {
            this.delegate = delegate;
            this.cache = cache;
            this.closeTimeout = closeTimeout;
            this.unit = unit;
        }

        @Override
        public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
            return this.delegate.send(record);
        }

        @Override
        public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
            return this.delegate.send(record, callback);
        }

        @Override
        public void flush() {
            this.delegate.flush();
        }

        @Override
        public List<PartitionInfo> partitionsFor(String topic) {
            return this.delegate.partitionsFor(topic);
        }

        @Override
        public Map<MetricName, ? extends Metric> metrics() {
            return this.delegate.metrics();
        }

        @Override
        public void initTransactions() {
            this.delegate.initTransactions();
        }

        @Override
        public void beginTransaction() throws ProducerFencedException {
            this.delegate.beginTransaction();
        }

        @Override
        public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, String consumerGroupId)
                throws ProducerFencedException {
            this.delegate.sendOffsetsToTransaction(offsets, consumerGroupId);
        }

        @Override
        public void commitTransaction() throws ProducerFencedException {
            this.delegate.commitTransaction();
        }

        @Override
        public void abortTransaction() throws ProducerFencedException {
            this.delegate.abortTransaction();
        }

        @Override
        public void close() {
            close(this.closeTimeout, unit);
        }

        @Override
        public void close(long timeout, TimeUnit unit) {
            boolean isAdded = this.cache.offer(this);
            if (!isAdded) {
                this.delegate.close(timeout, unit);
            }
        }

        @Override
        public String toString() {
            return "CloseLazyProducer [delegate=" + this.delegate + "]";
        }
    }

    /**
     * Builder class to instantiate a {@link DefaultProducerFactory}.
     * <p>
     * The {@code closeTimeout} is defaulted to {@code 30}, the {@link TimeUnit} for the {@code closeTimeout} is
     * defaulted to {@link TimeUnit#SECONDS}, the {@code producerCacheSize} defaults to {@code 10} and the
     * {@link ConfirmationMode} is defaulted to {@link ConfirmationMode#NONE}.
     * The {@code configuration} is a <b>hard requirement</b> and as such should be provided.
     *
     * @param <K> a generic type for the key of the {@link Producer} this {@link ProducerFactory} will create
     * @param <V> a generic type for the value of the {@link Producer} this {@link ProducerFactory} will create
     */
    public static final class Builder<K, V> {

        private int closeTimeout = 30;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private int producerCacheSize = 10;
        private Map<String, Object> configuration;
        private ConfirmationMode confirmationMode = ConfirmationMode.NONE;
        private String transactionIdPrefix;

        /**
         * Set the {@code closeTimeout} specifying how long to wait when {@link Producer#close(long, TimeUnit)} is
         * invoked. Defaults to {@code 30} {@link TimeUnit#SECONDS}.
         *
         * @param timeout  the time to wait before invoking {@link Producer#close(long, TimeUnit)}. in units of
         *                 {@code timeUnit}.
         * @param timeUnit a {@code TimeUnit} determining how to interpret the {@code timeout} parameter
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> closeTimeout(int timeout, TimeUnit timeUnit) {
            assertThat(timeout, time -> time > 0, "The closeTimeout should be a positive number");
            assertNonNull(timeUnit, "The timeUnit may not be null");
            this.closeTimeout = timeout;
            this.timeUnit = timeUnit;
            return this;
        }

        /**
         * Sets the number of {@link Producer} instances to cache. Defaults to {@code 10}.
         * <p>
         * Will instantiate an {@link ArrayBlockingQueue} based on this number.
         *
         * @param producerCacheSize an {@code int} specifying the number of {@link Producer} instances to cache
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> producerCacheSize(int producerCacheSize) {
            assertThat(producerCacheSize, size -> size > 0, "The producerCacheSize should be a positive number");
            this.producerCacheSize = producerCacheSize;
            return this;
        }

        /**
         * Sets the {@code configuration} properties for creating {@link Producer} instances.
         *
         * @param configuration a {@link Map} of {@link String} to {@link Object} containing Kafka properties for
         *                      creating {@link Producer} instances
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> configuration(Map<String, Object> configuration) {
            assertNonNull(configuration, "The configuration may not be null");
            this.configuration = Collections.unmodifiableMap(new HashMap<>(configuration));
            return this;
        }

        /**
         * Sets the {@link ConfirmationMode} for producing {@link Producer} instances. Defaults to
         * {@link ConfirmationMode#NONE}.
         *
         * @param confirmationMode the {@link ConfirmationMode} for producing {@link Producer} instances
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> confirmationMode(ConfirmationMode confirmationMode) {
            assertNonNull(confirmationMode, "ConfirmationMode may not be null");
            this.confirmationMode = confirmationMode;
            return this;
        }

        /**
         * Sets the prefix to generate the {@code transactional.id} required for transactional {@link Producer}s.
         *
         * @param transactionIdPrefix a {@link String} specifying the prefix used to generate the
         *                            {@code transactional.id} required for transactional {@link Producer}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<K, V> transactionalIdPrefix(String transactionIdPrefix) {
            this.transactionIdPrefix = transactionIdPrefix;
            return this.confirmationMode(ConfirmationMode.TRANSACTIONAL);
        }

        /**
         * Initializes a {@link DefaultProducerFactory} as specified through this Builder.
         *
         * @return a {@link DefaultProducerFactory} as specified through this Builder
         */
        public DefaultProducerFactory<K, V> build() {
            return new DefaultProducerFactory<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(configuration, "The configuration is a hard requirement and should be provided");
        }
    }
}

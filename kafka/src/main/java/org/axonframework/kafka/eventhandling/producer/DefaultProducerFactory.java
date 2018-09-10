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
import org.axonframework.common.Assert;
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

/**
 * The {@link ProducerFactory} implementation to produce a {@code singleton} shared {@link Producer}
 * instance.
 * <p>
 * The {@link Producer} instance is freed from the external {@link Producer#close()} invocation
 * with the internal wrapper. The real {@link Producer#close()} is called on the target
 * {@link Producer} during the {@link #shutDown()}.
 * <p>
 * Setting {@link Builder#withConfirmationMode(ConfirmationMode)} to transactional produces a transactional producer; in
 * which case,
 * a cache of producers is maintained; closing the producer returns it to the cache.
 * If cache is full the producer will be closed {@link KafkaProducer#close(long, TimeUnit)} and evicted from cache.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public class DefaultProducerFactory<K, V> implements ProducerFactory<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProducerFactory.class);

    private final int closeTimeout;
    private final TimeUnit unit;
    private final BlockingQueue<CloseLazyProducer<K, V>> cache;
    private final Map<String, Object> configs;
    private final ConfirmationMode confirmationMode;
    private final String transactionIdPrefix;
    private final AtomicInteger transactionIdSuffix;

    private volatile CloseLazyProducer<K, V> producer;

    private DefaultProducerFactory(Builder<K, V> builder) {
        this.closeTimeout = builder.closeTimeout;
        this.unit = builder.unit;
        this.cache = new ArrayBlockingQueue<>(builder.producerCacheSize);
        this.configs = new HashMap<>(builder.configs);
        this.confirmationMode = builder.confirmationMode;
        this.transactionIdPrefix = builder.transactionIdPrefix;
        this.transactionIdSuffix = new AtomicInteger();
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
                    this.producer = new CloseLazyProducer<>(
                            createKafkaProducer(configs), cache, closeTimeout, unit);
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
     * @return the configs.
     */
    public Map<String, Object> configurationProperties() {
        return Collections.unmodifiableMap(configs);
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
            producer.delegate.close(this.closeTimeout, unit);
        }
        producer = this.cache.poll();
        while (producer != null) {
            try {
                producer.delegate.close(this.closeTimeout, unit);
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
        Map<String, Object> configs = new HashMap<>(this.configs);
        configs.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,
                    this.transactionIdPrefix + this.transactionIdSuffix.getAndIncrement());
        producer = new CloseLazyProducer<>(createKafkaProducer(configs), cache, closeTimeout, unit);
        producer.initTransactions();
        return producer;
    }

    /**
     * @param configs Kafka properties for creating a producer(s).
     * @param <K>     key type.
     * @param <V>     value type.
     * @return the builder.
     */
    public static <K, V> Builder<K, V> builder(Map<String, Object> configs) {
        return new Builder<>(configs);
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

    public static final class Builder<K, V> {

        private final Map<String, Object> configs;

        private String transactionIdPrefix;
        private int producerCacheSize = 10;
        private int closeTimeout = 30;
        private TimeUnit unit = TimeUnit.SECONDS;
        private ConfirmationMode confirmationMode = ConfirmationMode.NONE;

        /**
         * @param configs Kafka properties for creating a producer(s).
         */
        private Builder(Map<String, Object> configs) {
            Assert.notNull(configs, () -> "'configs' may not be null");
            this.configs = Collections.unmodifiableMap(new HashMap<>(configs));
        }

        /**
         * How many producer instances to cache. Default to 10.
         *
         * @param producerCacheSize the cache size.
         * @return the builder.
         */
        public Builder<K, V> withProducerCacheSize(int producerCacheSize) {
            Assert.isTrue(producerCacheSize > 0, () -> "'producerCacheSize should be > 0");
            this.producerCacheSize = producerCacheSize;
            return this;
        }

        /**
         * How long to wait when {@link Producer#close(long, TimeUnit)} is invoked. Default is 30 seconds.
         *
         * @param timeout how long to wait before closing a producer, in units of
         *                {@code unit}.
         * @param unit    a {@code TimeUnit} determining how to interpret the
         *                {@code timeout} parameter.
         * @return the builder.
         */
        public Builder<K, V> withCloseTimeout(int timeout, TimeUnit unit) {
            Assert.isTrue(timeout > 0, () -> "'closeTimeout' should be > 0");
            Assert.notNull(unit, () -> "'timeUnit' may not be null");
            this.closeTimeout = timeout;
            this.unit = unit;
            return this;
        }

        /**
         * Mode for producing {@link Producer}.
         *
         * @param confirmationMode, default to {@link ConfirmationMode#NONE}.
         * @return the builder.
         */
        public Builder<K, V> withConfirmationMode(ConfirmationMode confirmationMode) {
            Assert.notNull(confirmationMode, () -> "'confirmationMode' may not be null");
            this.confirmationMode = confirmationMode;
            return this;
        }

        /**
         * Transactional id prefix.
         *
         * @param transactionIdPrefix prefix to generate <code>transactional.id</code>. Required for transactional
         *                            producers.
         * @return the builder.
         */
        public Builder<K, V> withTransactionalIdPrefix(String transactionIdPrefix) {
            Assert.notNull(transactionIdPrefix, () -> "'transactionIdPrefix' cannot be null");
            this.transactionIdPrefix = transactionIdPrefix;
            return this.withConfirmationMode(ConfirmationMode.TRANSACTIONAL);
        }

        public ProducerFactory<K, V> build() {
            return new DefaultProducerFactory<>(this);
        }
    }
}

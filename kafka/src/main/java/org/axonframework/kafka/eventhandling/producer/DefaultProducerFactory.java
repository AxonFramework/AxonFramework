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
    private final TimeUnit timeoutUnit;
    private final BlockingQueue<CloseLazyProducer<K, V>> cache;
    private final Map<String, Object> configs;
    private final ConfirmationMode confirmationMode;
    private final String transactionIdPrefix;

    private final AtomicInteger transactionIdSuffix = new AtomicInteger();

    private volatile CloseLazyProducer<K, V> producer;

    private DefaultProducerFactory(Builder<K, V> builder) {
        this.closeTimeout = builder.closeTimeout;
        this.timeoutUnit = builder.timeoutUnit;
        this.configs = new HashMap<>(builder.configs);
        this.cache = new ArrayBlockingQueue<>(builder.producerCacheSize);
        this.confirmationMode = builder.confirmationMode;
        this.transactionIdPrefix = builder.transactionIdPrefix;
    }

    /**
     * Closes all producer instances.
     */
    public void shutDown() {
        CloseLazyProducer<K, V> producer = this.producer;
        this.producer = null;
        if (producer != null) {
            producer.delegate.close(this.closeTimeout, timeoutUnit);
        }
        producer = this.cache.poll();
        while (producer != null) {
            try {
                producer.delegate.close(this.closeTimeout, timeoutUnit);
            } catch (Exception e) {
                logger.error("Exception closing producer", e);
            }
            producer = this.cache.poll();
        }
    }

    @Override
    public Producer<K, V> createProducer() {
        if (confirmationMode.isTransactional()) {
            return createTransactionalProducer();
        }
        if (this.producer == null) {
            synchronized (this) {
                if (this.producer == null) {
                    this.producer = new CloseLazyProducer<>(createKafkaProducer(configs), cache, closeTimeout, timeoutUnit);
                }
            }
        }
        return this.producer;
    }

    private Producer<K, V> createTransactionalProducer() {
        Producer<K, V> producer = this.cache.poll();
        if (producer != null) {
            return producer;
        }
        Map<String, Object> configs = new HashMap<>(this.configs);
        configs.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,
                    this.transactionIdPrefix + this.transactionIdSuffix.getAndIncrement());
        producer = new CloseLazyProducer<>(createKafkaProducer(configs), cache, closeTimeout, timeoutUnit);
        producer.initTransactions();
        return producer;
    }

    public ConfirmationMode getConfirmationMode() {
        return confirmationMode;
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    private Producer<K, V> createKafkaProducer(Map<String, Object> configs) {
        return new KafkaProducer<>(configs);
    }

    private static final class CloseLazyProducer<K, V> implements Producer<K, V> {

        private final Producer<K, V> delegate;
        private final BlockingQueue<CloseLazyProducer<K, V>> cache;
        private final int closeTimeout;
        private final TimeUnit timeoutUnit;

        CloseLazyProducer(Producer<K, V> delegate, BlockingQueue<CloseLazyProducer<K, V>> cache, int closeTimeout,
                          TimeUnit timeoutUnit) {
            this.delegate = delegate;
            this.cache = cache;
            this.closeTimeout = closeTimeout;
            this.timeoutUnit = timeoutUnit;
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
            close(this.closeTimeout, timeoutUnit);
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

        private Map<String, Object> configs;
        private int producerCacheSize = 10;
        private int closeTimeout = 30;
        private TimeUnit timeoutUnit = TimeUnit.SECONDS;
        private ConfirmationMode confirmationMode = ConfirmationMode.NONE;
        private String transactionIdPrefix;

        /**
         * Kafka properties for creating a producer(s)
         *
         * @param configs kafka properties
         * @return Builder
         */
        public Builder<K, V> withConfigs(Map<String, Object> configs) {
            this.configs = configs;
            return this;
        }

        /**
         * How many producer instances to cache. Default to 10
         *
         * @param producerCacheSize cache size
         * @return Builder
         */
        public Builder<K, V> withProducerCacheSize(int producerCacheSize) {
            Assert.isTrue(producerCacheSize > 0, () -> "Cache size should be > 0");
            this.producerCacheSize = producerCacheSize;
            return this;
        }

        /**
         * How long to wait when {@link Producer#close(long, TimeUnit)} is invoked. Default is 30 seconds
         *
         * @param closeTimeout timeout in seconds
         * @return Builder
         */
        public Builder<K, V> withCloseTimeout(int closeTimeout, TimeUnit timeUnit) {
            Assert.isTrue(closeTimeout > 0, () -> "timeout should be > 0");
            Assert.isTrue(timeUnit != null, () -> "TimeUnit may not be null");
            this.closeTimeout = closeTimeout;
            this.timeoutUnit = timeUnit;
            return this;
        }

        /**
         * Mode for producing {@link Producer}
         *
         * @param mode, default to {@link ConfirmationMode#NONE}
         * @return Builder
         */
        public Builder<K, V> withConfirmationMode(ConfirmationMode mode) {
            this.confirmationMode = mode;
            return this;
        }

        /**
         * Transactional id prefix
         *
         * @param transactionalIdPrefix prefix to generate transactional id
         * @return Builder
         */
        public Builder<K, V> withTransactionalIdPrefix(String transactionalIdPrefix) {
            this.transactionIdPrefix = transactionalIdPrefix;
            return this;
        }

        public DefaultProducerFactory<K, V> build() {
            return new DefaultProducerFactory<>(this);
        }
    }
}

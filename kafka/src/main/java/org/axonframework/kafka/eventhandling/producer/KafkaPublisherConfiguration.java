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

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.xml.XStreamSerializer;

/**
 * Configures {@link KafkaPublisher}.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 * @author Nakul Mishra
 * @since 3.0
 */
public class KafkaPublisherConfiguration<K, V> {

    private final SubscribableMessageSource<EventMessage<?>> messageSource;
    private final ProducerFactory<K, V> producerFactory;
    private final KafkaMessageConverter<K, V> messageConverter;
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;
    private final String topic;
    private final long publisherAckTimeout;

    private KafkaPublisherConfiguration(Builder<K, V> builder) {
        this.messageSource = builder.messageSource;
        this.producerFactory = builder.producerFactory;
        this.messageConverter = builder.messageConverter;
        this.messageMonitor = builder.messageMonitor;
        this.topic = builder.topic;
        this.publisherAckTimeout = builder.publisherAckTimeout;
    }

    public SubscribableMessageSource<EventMessage<?>> getMessageSource() {
        return messageSource;
    }

    public ProducerFactory<K, V> getProducerFactory() {
        return producerFactory;
    }

    public KafkaMessageConverter<K, V> getMessageConverter() {
        return messageConverter;
    }

    public MessageMonitor<? super EventMessage<?>> getMessageMonitor() {
        return messageMonitor;
    }

    public String getTopic() {
        return topic;
    }

    public long getPublisherAckTimeout() {
        return publisherAckTimeout;
    }

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    public static final class Builder<K, V> {

        private SubscribableMessageSource<EventMessage<?>> messageSource;
        private ProducerFactory<K, V> producerFactory;
        private KafkaMessageConverter<K, V> messageConverter =
                (KafkaMessageConverter<K, V>) new DefaultKafkaMessageConverter(new XStreamSerializer());
        private MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.instance();
        private String topic = "Axon.Events";
        private long publisherAckTimeout = 1000;

        /**
         * Configure {@link SubscribableMessageSource}.
         *
         * @param messageSource the message source.
         * @return the builder.
         */
        public Builder<K, V> withMessageSource(SubscribableMessageSource<EventMessage<?>> messageSource) {
            Assert.notNull(messageSource, () -> "Message source may not be null");
            this.messageSource = messageSource;
            return this;
        }

        /**
         * Configure {@link ProducerFactory}.
         *
         * @param producerFactory the producer factory.
         * @return the builder.
         */
        public Builder<K, V> withProducerFactory(ProducerFactory<K, V> producerFactory) {
            Assert.notNull(producerFactory, () -> "Producer factory may not be null");
            this.producerFactory = producerFactory;
            return this;
        }

        /**
         * Configure {@link KafkaMessageConverter}.
         *
         * @param messageConverter the message converter.
         * @return the builder.
         */
        public Builder<K, V> withMessageConverter(KafkaMessageConverter<K, V> messageConverter) {
            Assert.notNull(messageConverter, () -> "Message converter may not be null");
            this.messageConverter = messageConverter;
            return this;
        }

        /**
         * Configure {@link MessageMonitor}.
         *
         * @param messageMonitor the message monitor.
         * @return the builder.
         */
        public Builder<K, V> withMessageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
            Assert.notNull(messageMonitor, () -> "Message monitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Configure Kafka topic for publishing messages, default to <code>Axon.EventBus</code>.
         *
         * @param topic the topic.
         * @return the builder.
         */
        public Builder<K, V> withTopic(String topic) {
            Assert.notNull(topic, () -> "Topic may not be null");
            this.topic = topic;
            return this;
        }

        /**
         * @param timeoutInMillis how long to wait for publisher to acknowledge send, expressed in millis. Default to 1
         *                        sec.
         * @return the builder.
         */
        public Builder<K, V> withPublisherAckTimeout(long timeoutInMillis) {
            Assert.isTrue(timeoutInMillis >= 0, () -> "Timeout may not be negative");
            this.publisherAckTimeout = timeoutInMillis;
            return this;
        }

        public KafkaPublisherConfiguration<K, V> build() {
            Assert.notNull(producerFactory, () -> "The publisher must be configured with a ProducerFactory");
            Assert.notNull(messageSource, () -> "The publisher must be configured with a MessageSource");
            return new KafkaPublisherConfiguration<>(this);
        }
    }
}

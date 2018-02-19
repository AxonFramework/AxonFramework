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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.monitoring.MessageMonitor;

/**
 * Configure settings used by {@link KafkaPublisher} for publishing messages to kafka
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
        private KafkaMessageConverter<K, V> messageConverter;
        private MessageMonitor<? super EventMessage<?>> messageMonitor;
        private String topic = "Axon.EventBus";
        private long publisherAckTimeout = 1000;

        public Builder<K, V> withMessageSource(SubscribableMessageSource<EventMessage<?>> messageSource) {
            this.messageSource = messageSource;
            return this;
        }

        public Builder<K, V> withProducerFactory(ProducerFactory<K, V> producerFactory) {
            this.producerFactory = producerFactory;
            return this;
        }

        public Builder<K, V> withMessageConverter(KafkaMessageConverter<K, V> messageConverter) {
            this.messageConverter = messageConverter;
            return this;
        }

        public Builder<K, V> withMessageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * @param topicName kafka topic to publish messages. By default it will try to create a topic Axon.EventBus
         * @return Builder
         */
        public Builder<K, V> withTopic(String topicName) {
            this.topic = topicName;
            return this;
        }

        /**
         * @param timeoutInMillis how long to wait for publisher to acknowledge send, expressed in millis. Default to 1
         *                        sec
         * @return Builder
         */
        public Builder<K, V> withPublisherAckTimeout(long timeoutInMillis) {
            this.publisherAckTimeout = timeoutInMillis;
            return this;
        }

        public KafkaPublisherConfiguration<K, V> build() {
            return new KafkaPublisherConfiguration<>(this);
        }
    }
}

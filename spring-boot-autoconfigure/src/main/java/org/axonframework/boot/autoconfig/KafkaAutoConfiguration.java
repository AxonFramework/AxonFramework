/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.boot.autoconfig;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.kafka.eventhandling.consumer.ConsumerFactory;
import org.axonframework.kafka.eventhandling.consumer.DefaultConsumerFactory;
import org.axonframework.kafka.eventhandling.consumer.KafkaMessageSource;
import org.axonframework.kafka.eventhandling.producer.ConfirmationMode;
import org.axonframework.kafka.eventhandling.producer.DefaultProducerFactory;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisher;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisherConfiguration;
import org.axonframework.kafka.eventhandling.producer.ProducerFactory;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Apache Kafka.
 *
 * @author Nakul Mishra
 * @since 3.0
 */

@Configuration
@ConditionalOnClass(KafkaPublisher.class)
@EnableConfigurationProperties(KafkaProperties.class)
@AutoConfigureAfter({AxonAutoConfiguration.class})
public class KafkaAutoConfiguration {

    private final KafkaProperties properties;
    private final KafkaMessageConverter<String, byte[]> messageConverter;

    public KafkaAutoConfiguration(KafkaProperties properties,
                                  KafkaMessageConverter<String, byte[]> messageConverter) {
        this.properties = properties;
        this.messageConverter = messageConverter;
    }

    @ConditionalOnMissingBean
    @Bean
    public DefaultProducerFactory.Builder<String, byte[]> producerBuilder() {
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        return DefaultProducerFactory.<String, byte[]>builder()
                .withConfirmationMode(ConfirmationMode.TRANSACTIONAL)
                .withConfigs(properties.buildProducerProperties());
    }

    @ConditionalOnMissingBean
    @Bean
    public ProducerFactory<String, byte[]> producerFactory(DefaultProducerFactory.Builder<String, byte[]> builder) {
        return builder.build();
    }

    @ConditionalOnMissingBean
    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                    "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return new DefaultConsumerFactory<>(properties.buildConsumerProperties());
    }

    @ConditionalOnMissingBean
    @Bean(initMethod = "start", destroyMethod = "shutDown")
    public KafkaPublisher<String, byte[]> publisher(KafkaPublisherConfiguration<String, byte[]> config) {
        return new KafkaPublisher<>(config);
    }

    @ConditionalOnMissingBean
    @Bean
    public KafkaPublisherConfiguration<String, byte[]> publisherConfig(ProducerFactory<String, byte[]> producerFactory,
                                                                       EventBus eventBus,
                                                                       AxonConfiguration configuration) {
        return KafkaPublisherConfiguration.<String, byte[]>builder()
                .withTopic(properties.getDefaultTopic())
                .withMessageConverter(messageConverter)
                .withProducerFactory(producerFactory)
                .withMessageSource(eventBus)
                .withMessageMonitor(configuration.messageMonitor(EventStore.class, "eventStore"))
                .build();
    }

    @ConditionalOnMissingBean
    @Bean
    public KafkaMessageSource<String, byte[]> kafkaMessageSource(ConsumerFactory<String, byte[]> consumerFactory) {
        return new KafkaMessageSource<>(consumerFactory, messageConverter, properties.getDefaultTopic());
    }

    @ConditionalOnMissingBean
    @Bean
    public KafkaMessageConverter<String, byte[]> messageConverter(Serializer eventSerializer) {
        return new DefaultKafkaMessageConverter(eventSerializer);
    }
}

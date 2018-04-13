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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.kafka.eventhandling.consumer.ConsumerFactory;
import org.axonframework.kafka.eventhandling.consumer.DefaultConsumerFactory;
import org.axonframework.kafka.eventhandling.consumer.DefaultFetcher;
import org.axonframework.kafka.eventhandling.consumer.Fetcher;
import org.axonframework.kafka.eventhandling.consumer.KafkaMessageSource;
import org.axonframework.kafka.eventhandling.consumer.MessageBuffer;
import org.axonframework.kafka.eventhandling.producer.ConfirmationMode;
import org.axonframework.kafka.eventhandling.producer.DefaultProducerFactory;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisher;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisherConfiguration;
import org.axonframework.kafka.eventhandling.producer.ProducerFactory;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

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

    public KafkaAutoConfiguration(KafkaProperties properties) {
        this.properties = properties;
    }

    @ConditionalOnMissingBean
    @ConditionalOnProperty("axon.kafka.producer.transaction-id-prefix")
    @Bean
    public ProducerFactory<String, byte[]> producerFactory() {
        Map<String, Object> producer = properties.buildProducerProperties();
        String transactionIdPrefix = properties.getProducer().getTransactionIdPrefix();
        if (transactionIdPrefix == null) {
            throw new IllegalStateException("transactionalIdPrefix cannot be empty");
        }
        return DefaultProducerFactory.<String, byte[]>builder(producer)
                .withConfirmationMode(ConfirmationMode.TRANSACTIONAL)
                .withTransactionalIdPrefix(transactionIdPrefix)
                .build();
    }

    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty("axon.kafka.consumer.group-id")
    public ConsumerFactory<String, byte[]> consumerFactory() {
        return new DefaultConsumerFactory<>(properties.buildConsumerProperties());
    }

    @ConditionalOnMissingBean
    @Bean(initMethod = "start", destroyMethod = "shutDown")
    @ConditionalOnBean(ProducerFactory.class)
    public KafkaPublisher<String, byte[]> publisher(ProducerFactory<String, byte[]> producerFactory,
                                                    EventBus eventBus,
                                                    KafkaMessageConverter<String, byte[]> messageConverter,
                                                    AxonConfiguration configuration) {
        return new KafkaPublisher<>(KafkaPublisherConfiguration.<String, byte[]>builder()
                                            .withTopic(properties.getDefaultTopic())
                                            .withMessageConverter(messageConverter)
                                            .withProducerFactory(producerFactory)
                                            .withMessageSource(eventBus)
                                            .withMessageMonitor(configuration
                                                                        .messageMonitor(EventStore.class, "eventStore"))
                                            .build());
    }

    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnBean(ProducerFactory.class)
    public Fetcher<String, byte[]> fetcher(ConsumerFactory<String, byte[]> consumerFactory,
                                           KafkaMessageConverter<String, byte[]> messageConverter) {
        return DefaultFetcher.builder(properties.getDefaultTopic(),
                                      consumerFactory,
                                      messageConverter,
                                      new MessageBuffer<>(properties.getFetcher().getBufferSize()),
                                      properties.getFetcher().getPollTimeout()).build();
    }

    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnBean(ConsumerFactory.class)
    public KafkaMessageSource<String, byte[]> kafkaMessageSource(Fetcher<String, byte[]> fetcher) {
        return new KafkaMessageSource<>(fetcher);
    }

    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnBean(ProducerFactory.class)
    public KafkaMessageConverter<String, byte[]> messageConverter(
            @Qualifier("eventSerializer") Serializer eventSerializer) {
        return new DefaultKafkaMessageConverter(eventSerializer);
    }
}

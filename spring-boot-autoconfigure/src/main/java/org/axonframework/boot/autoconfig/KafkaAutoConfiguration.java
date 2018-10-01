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

import org.axonframework.boot.KafkaProperties;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.kafka.eventhandling.DefaultKafkaMessageConverter;
import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.kafka.eventhandling.consumer.AsyncFetcher;
import org.axonframework.kafka.eventhandling.consumer.ConsumerFactory;
import org.axonframework.kafka.eventhandling.consumer.DefaultConsumerFactory;
import org.axonframework.kafka.eventhandling.consumer.Fetcher;
import org.axonframework.kafka.eventhandling.consumer.KafkaMessageSource;
import org.axonframework.kafka.eventhandling.consumer.SortedKafkaMessageBuffer;
import org.axonframework.kafka.eventhandling.producer.ConfirmationMode;
import org.axonframework.kafka.eventhandling.producer.DefaultProducerFactory;
import org.axonframework.kafka.eventhandling.producer.KafkaPublisher;
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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

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
    public ProducerFactory<String, byte[]> kafkaProducerFactory() {
        Map<String, Object> producer = properties.buildProducerProperties();
        String transactionIdPrefix = properties.getProducer().getTransactionIdPrefix();
        if (transactionIdPrefix == null) {
            throw new IllegalStateException("transactionalIdPrefix cannot be empty");
        }
        return DefaultProducerFactory.<String, byte[]>builder()
                .configuration(producer)
                .confirmationMode(ConfirmationMode.TRANSACTIONAL)
                .transactionalIdPrefix(transactionIdPrefix)
                .build();
    }

    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty("axon.kafka.consumer.group-id")
    public ConsumerFactory<String, byte[]> kafkaConsumerFactory() {
        return new DefaultConsumerFactory<>(properties.buildConsumerProperties());
    }

    @ConditionalOnMissingBean
    @Bean
    public KafkaMessageConverter<String, byte[]> kafkaMessageConverter(
            @Qualifier("eventSerializer") Serializer eventSerializer) {
        return DefaultKafkaMessageConverter.builder().serializer(eventSerializer).build();
    }

    @ConditionalOnMissingBean
    @Bean(initMethod = "start", destroyMethod = "shutDown")
    @ConditionalOnBean({ProducerFactory.class, KafkaMessageConverter.class})
    public KafkaPublisher<String, byte[]> kafkaPublisher(ProducerFactory<String, byte[]> kafkaProducerFactory,
                                                         EventBus eventBus,
                                                         KafkaMessageConverter<String, byte[]> kafkaMessageConverter,
                                                         AxonConfiguration configuration) {
        return KafkaPublisher.<String, byte[]>builder()
                .messageSource(eventBus)
                .producerFactory(kafkaProducerFactory)
                .messageConverter(kafkaMessageConverter)
                .messageMonitor(configuration.messageMonitor(KafkaPublisher.class, "kafkaPublisher"))
                .topic(properties.getDefaultTopic())
                .build();
    }

    @ConditionalOnMissingBean
    @ConditionalOnBean({ConsumerFactory.class, KafkaMessageConverter.class})
    @Bean(destroyMethod = "shutdown")
    public Fetcher kafkaFetcher(ConsumerFactory<String, byte[]> kafkaConsumerFactory,
                                KafkaMessageConverter<String, byte[]> kafkaMessageConverter) {
        return AsyncFetcher.<String, byte[]>builder()
                .consumerFactory(kafkaConsumerFactory)
                .bufferFactory(() -> new SortedKafkaMessageBuffer<>(properties.getFetcher().getBufferSize()))
                .messageConverter(kafkaMessageConverter)
                .topic(properties.getDefaultTopic())
                .pollTimeout(properties.getFetcher().getPollTimeout(), MILLISECONDS)
                .build();
    }

    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnBean(ConsumerFactory.class)
    public KafkaMessageSource kafkaMessageSource(Fetcher kafkaFetcher) {
        return new KafkaMessageSource(kafkaFetcher);
    }
}

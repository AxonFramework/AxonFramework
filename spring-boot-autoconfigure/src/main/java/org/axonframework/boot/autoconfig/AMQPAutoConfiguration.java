/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot.autoconfig;

import org.axonframework.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.amqp.eventhandling.PackageRoutingKeyResolver;
import org.axonframework.amqp.eventhandling.RoutingKeyResolver;
import org.axonframework.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.axonframework.boot.AMQPProperties;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.serialization.Serializer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(SpringAMQPPublisher.class)
@EnableConfigurationProperties(AMQPProperties.class)
@AutoConfigureAfter({RabbitAutoConfiguration.class, AxonAutoConfiguration.class})
public class AMQPAutoConfiguration {

    @Autowired
    private AMQPProperties amqpProperties;

    @ConditionalOnMissingBean
    @Bean
    public RoutingKeyResolver routingKeyResolver() {
        return new PackageRoutingKeyResolver();
    }

    @ConditionalOnMissingBean
    @Bean
    public AMQPMessageConverter amqpMessageConverter(Serializer serializer, RoutingKeyResolver routingKeyResolver) {
        return new DefaultAMQPMessageConverter(serializer, routingKeyResolver, amqpProperties.isDurableMessages());
    }

    @ConditionalOnProperty("axon.amqp.exchange")
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnMissingBean
    @Bean(initMethod = "start", destroyMethod = "shutDown")
    public SpringAMQPPublisher amqpBridge(EventBus eventBus, ConnectionFactory connectionFactory,
                                          AMQPMessageConverter amqpMessageConverter) {
        SpringAMQPPublisher publisher = new SpringAMQPPublisher(eventBus);
        publisher.setExchangeName(amqpProperties.getExchange());
        publisher.setConnectionFactory(connectionFactory);
        publisher.setMessageConverter(amqpMessageConverter);
        switch (amqpProperties.getTransactionMode()) {

            case TRANSACTIONAL:
                publisher.setTransactional(true);
                break;
            case PUBLISHER_ACK:
                publisher.setWaitForPublisherAck(true);
                break;
            case NONE:
                break;
            default:
                throw new IllegalStateException("Unknown transaction mode: " + amqpProperties.getTransactionMode());
        }
        return publisher;
    }
}

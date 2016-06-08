/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.contextsupport.spring.amqp;

import org.aopalliance.aop.Advice;
import org.axonframework.common.DirectExecutor;
import org.axonframework.eventhandling.amqp.AMQPMessageConverter;
import org.axonframework.eventhandling.amqp.RoutingKeyResolver;
import org.axonframework.eventhandling.amqp.spring.SpringAMQPConsumerConfiguration;
import org.axonframework.eventhandling.amqp.spring.SpringAMQPEventBus;
import org.axonframework.serialization.Serializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ErrorHandler;

import java.util.concurrent.Executor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(classes = EventBusBeanDefinitionParserTest.Context.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class EventBusBeanDefinitionParserTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    public void testContextStartup() {
        assertNotNull(applicationContext);
        assertNotNull(beanFactory);
        BeanDefinition terminal1 = beanFactory.getBeanDefinition("terminal1");
        assertEquals(SpringAMQPEventBus.class.getName(), terminal1.getBeanClassName());
        assertTrue(property(terminal1, "listenerContainerLifecycleManager") instanceof BeanReference);

        assertEquals("connectionFactory", property(terminal1, "connectionFactory", BeanReference.class).getBeanName());
        assertEquals("messageConverter", property(terminal1, "messageConverter", BeanReference.class).getBeanName());
        assertEquals("routingKeyResolver", property(terminal1, "routingKeyResolver", BeanReference.class).getBeanName());
        assertEquals("serializer", property(terminal1, "serializer", BeanReference.class).getBeanName());
        assertEquals("true", property(terminal1, "durable", String.class));
        assertEquals("false", property(terminal1, "transactional", String.class));
        assertEquals("true", property(terminal1, "waitForPublisherAck", String.class));
        assertEquals("Exchange", property(terminal1, "exchangeName", String.class));

        final BeanDefinition containerManager = beanFactory.getBeanDefinition("terminal1$containerManager");
        assertNotNull(containerManager);

        assertNotNull(applicationContext.getBean("terminal1"));
        assertNotNull(applicationContext.getBean("terminal1$containerManager"));
    }

    private Object property(BeanDefinition bean, String propertyName) {
        final PropertyValue propertyValue = bean.getPropertyValues().getPropertyValue(propertyName);
        if (propertyValue == null) {
            return null;
        }
        return propertyValue.getValue();
    }

    private <T> T property(BeanDefinition bean, String propertyName, Class<T> expectedType) {
        Object property = property(bean, propertyName);
        assertNotNull(propertyName + " is not set", property);
        assertTrue(propertyName + " is not a " + expectedType.getSimpleName(),
                   expectedType.isInstance(property));
        return expectedType.cast(property);
    }

    @Test
    public void testConfigurationBean() {
        SpringAMQPConsumerConfiguration config = applicationContext.getBean("fullConfig",
                                                                            SpringAMQPConsumerConfiguration.class);
        assertEquals(AcknowledgeMode.MANUAL, config.getAcknowledgeMode());
        assertNotNull(config.getAdviceChain());
        assertEquals((Integer)1, config.getConcurrentConsumers());
        assertEquals("Queue", config.getQueueName());
        assertNotNull(config.getTaskExecutor());
        assertNotNull(config.getErrorHandler());
        assertEquals((Long)10L, config.getReceiveTimeout());
        assertEquals((Long)1000L, config.getRecoveryInterval());
        assertEquals((Long)10L, config.getShutdownTimeout());
        assertNotNull(config.getTransactionManager());
        assertTrue(config.getExclusive());
        assertEquals((Integer) 200, config.getTxSize());
        assertEquals((Integer) 1000, config.getPrefetchCount());
    }

    @Configuration
    @ImportResource("/META-INF/spring/amqp-namespace-context.xml")
    public static class Context {

        @Bean
        public ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }

        @Bean
        public Serializer serializer() {
            return mock(Serializer.class);
        }

        @Bean
        public PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        public AMQPMessageConverter messageConverter() {
            return mock(AMQPMessageConverter.class);
        }
        @Bean
        public RoutingKeyResolver routingKeyResolver() {
            return mock(RoutingKeyResolver.class);
        }

        @Bean
        public ErrorHandler errorHandler() {
            return mock(ErrorHandler.class);
        }

        @Bean
        public Executor executor() {
            return new DirectExecutor();
        }

        @Bean
        public Advice[] mockAdviceChain() {
            return new Advice[] {mock(Advice.class)};
        }
    }
}

/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.boot;

import org.axonframework.amqp.eventhandling.AMQPMessageConverter;
import org.axonframework.amqp.eventhandling.DefaultAMQPMessageConverter;
import org.axonframework.amqp.eventhandling.spring.SpringAMQPPublisher;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.serialization.JavaSerializer;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.lang.reflect.Field;

import static org.axonframework.common.ReflectionUtils.getFieldValue;
import static org.junit.Assert.*;

@ContextConfiguration
@EnableAutoConfiguration(exclude = {JmxAutoConfiguration.class, WebClientAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class})
@RunWith(SpringRunner.class)
@EnableConfigurationProperties
@EnableRabbit
public class AxonAutoConfigurationWithAMQPAndEventSerializer {

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeClass
    public static void setUp() {
        System.setProperty("axon.amqp.exchange", "test");
    }

    @Test
    public void testContextInitialization() throws Exception {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        AxonConfiguration axonConfiguration = applicationContext.getBean(AxonConfiguration.class);
        assertNotSame(axonConfiguration.serializer(), axonConfiguration.eventSerializer());

        AMQPMessageConverter amqpMessageConverter = applicationContext.getBean(AMQPMessageConverter.class);
        assertNotNull(amqpMessageConverter);
        Field serializerField = DefaultAMQPMessageConverter.class.getDeclaredField("serializer");
        Serializer serializer = getFieldValue(serializerField, amqpMessageConverter);
        assertTrue(serializer instanceof XStreamSerializer);

        assertNotNull(applicationContext.getBean(SpringAMQPPublisher.class));

        assertEquals(JavaSerializer.class, applicationContext.getBean(Serializer.class).getClass());
        assertEquals(XStreamSerializer.class,
                     applicationContext.getBean("myEventSerializer", Serializer.class).getClass());
    }

    @org.springframework.context.annotation.Configuration
    public static class Configuration {

        @Bean
        @Primary
        public Serializer mySerializer() {
            return JavaSerializer.builder().build();
        }

        @Bean
        @Qualifier("eventSerializer")
        public Serializer myEventSerializer() {
            return XStreamSerializer.builder().build();
        }
    }
}

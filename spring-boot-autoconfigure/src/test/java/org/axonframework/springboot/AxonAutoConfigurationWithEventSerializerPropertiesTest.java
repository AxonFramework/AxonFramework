/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.springboot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "axon.axonserver.enabled=false")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AxonAutoConfigurationWithEventSerializerPropertiesTest.TestContext.class)
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource("classpath:application.serializertest.properties")
class AxonAutoConfigurationWithEventSerializerPropertiesTest {

    @Autowired
    private ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void contextInitialization() {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(EventGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertNotNull(applicationContext.getBean("messageSerializer", Serializer.class));
        assertNotNull(applicationContext.getBean("eventSerializer", Serializer.class));
        org.axonframework.config.Configuration axonConfiguration =
                applicationContext.getBean(org.axonframework.config.Configuration.class);
        assertNotSame(axonConfiguration.serializer(), axonConfiguration.eventSerializer());
        assertSame(axonConfiguration.serializer(), axonConfiguration.messageSerializer());
        assertNotSame(axonConfiguration.messageSerializer(), axonConfiguration.eventSerializer());
        assertNotNull(applicationContext.getBean(TokenStore.class));
        assertNotNull(applicationContext.getBean(JpaEventStorageEngine.class));
        assertEquals(SQLErrorCodesResolver.class,
                     applicationContext.getBean(PersistenceExceptionResolver.class).getClass());
        assertNotNull(applicationContext.getBean(EntityManagerProvider.class));
        assertNotNull(applicationContext.getBean(ConnectionProvider.class));

        // for some reason, this test picks up some entities used in other tests
        assertEquals(8, entityManager.getEntityManagerFactory().getMetamodel().getEntities().size());
    }

    @Test
    void eventStorageEngineUsesSerializerBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final Serializer eventSerializer = applicationContext.getBean("eventSerializer", Serializer.class);
        final Serializer messageSerializer = applicationContext.getBean("messageSerializer", Serializer.class);
        final JpaEventStorageEngine engine = applicationContext.getBean(JpaEventStorageEngine.class);

        assertTrue(messageSerializer instanceof JacksonSerializer);
        assertEquals(serializer, engine.getSnapshotSerializer());
        assertEquals(eventSerializer, engine.getEventSerializer());
    }

    @Test
    void eventSerializerIsOfTypeJacksonSerializerAndUsesDefinedObjectMapperBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final Serializer eventSerializer = applicationContext.getBean("eventSerializer", Serializer.class);
        final ObjectMapper objectMapper = applicationContext.getBean("testObjectMapper", ObjectMapper.class);

        assertTrue(serializer instanceof JacksonSerializer);
        assertTrue(((JacksonSerializer) serializer).getObjectMapper() instanceof CBORMapper);
        assertTrue(eventSerializer instanceof JacksonSerializer);
        assertEquals(objectMapper, ((JacksonSerializer) eventSerializer).getObjectMapper());
    }

    @Configuration
    public static class TestContext {

        @Bean("testObjectMapper")
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}

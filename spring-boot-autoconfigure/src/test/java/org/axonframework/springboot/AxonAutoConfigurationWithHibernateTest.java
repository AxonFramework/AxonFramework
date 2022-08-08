/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.Upcaster;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.springboot.autoconfig.AxonServerActuatorAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        AxonServerBusAutoConfiguration.class,
        AxonServerAutoConfiguration.class,
        AxonServerActuatorAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AxonAutoConfigurationWithHibernateTest {

    @Autowired
    private ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private Upcaster upcaster;

    @Autowired
    private Snapshotter snapshotter;

    @Test
    void testContextInitialization() {
        assertNotNull(applicationContext);
        assertNotNull(snapshotter);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(EventGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertNotNull(applicationContext.getBean(TokenStore.class));
        assertNotNull(applicationContext.getBean(JpaEventStorageEngine.class));
        assertEquals(SQLErrorCodesResolver.class, applicationContext.getBean(PersistenceExceptionResolver.class).getClass());
        assertNotNull(applicationContext.getBean(EntityManagerProvider.class));
        assertNotNull(applicationContext.getBean(ConnectionProvider.class));

        assertEquals(6, entityManager.getEntityManagerFactory().getMetamodel().getEntities().size());
    }

    @Transactional
    @Test
    public void testEventStorageEngingeUsesSerializerBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final JpaEventStorageEngine engine = applicationContext.getBean(JpaEventStorageEngine.class);

        assertEquals(serializer, engine.getSnapshotSerializer());

        engine.appendEvents(asEventMessage("hello"));
        List<? extends TrackedEventMessage<?>> events = engine.readEvents(null, false).collect(Collectors.toList());
        assertEquals(1, events.size());

        verify(upcaster).upcast(any());
    }

    @Configuration
    public static class Config {
        @Bean
        public EventUpcaster upcaster() {
            EventUpcaster mock = mock(EventUpcaster.class);
            when(mock.upcast(any())).thenAnswer(i -> i.getArgument(0));
            return mock;
        }
    }
}

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.Snapshotter;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.Upcaster;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "axon.axonserver.enabled=false")
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class AxonAutoConfigurationWithHibernateTest {

    @Autowired
    private ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private Upcaster upcaster;

    @Autowired
    private Snapshotter snapshotter;

    @Test
    void contextInitialization() {
        assertNotNull(applicationContext);
        assertNotNull(snapshotter);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(EventGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertNotNull(applicationContext.getBean(TokenStore.class));
        assertNotNull(applicationContext.getBean(JpaEventStorageEngine.class));
        Assertions.assertEquals(SQLErrorCodesResolver.class,
                                applicationContext.getBean(PersistenceExceptionResolver.class).getClass());
        assertNotNull(applicationContext.getBean(EntityManagerProvider.class));
        assertNotNull(applicationContext.getBean(ConnectionProvider.class));

        // for some reason, this test picks up some entities used in other tests
        assertEquals(8, entityManager.getEntityManagerFactory().getMetamodel().getEntities().size());
    }

    @Transactional
    @Test
    public void eventStorageEngineUsesSerializerBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final JpaEventStorageEngine engine = applicationContext.getBean(JpaEventStorageEngine.class);

        Assertions.assertEquals(serializer, engine.getSnapshotSerializer());

        engine.appendEvents(GenericEventMessage.asEventMessage("hello"));
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

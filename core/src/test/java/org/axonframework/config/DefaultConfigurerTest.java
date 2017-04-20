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

package org.axonframework.config;

import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.commandhandling.model.GenericJpaRepository;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.interceptors.TransactionManagingInterceptor;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.axonframework.config.AggregateConfigurer.defaultConfiguration;
import static org.junit.Assert.*;

public class DefaultConfigurerTest {

    @Test
    public void defaultConfigurationWithEventSourcing() throws Exception {
        Configuration config = DefaultConfigurer.defaultConfiguration()
                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                .configureCommandBus(c -> new AsynchronousCommandBus())
                .configureAggregate(StubAggregate.class)
                .buildConfiguration();
        config.start();

        FutureCallback<Object, Object> callback = new FutureCallback<>();
        config.commandBus().dispatch(GenericCommandMessage.asCommandMessage("test"), callback);
        assertEquals("test", callback.get());
        assertNotNull(config.repository(StubAggregate.class));
        assertEquals(1, config.getModules().size());
    }

    @Test
    public void defaultConfigurationWithUpcaster() throws Exception {
        EntityManager em = createEntityManager();
        AtomicInteger counter = new AtomicInteger();
        Configuration config = DefaultConfigurer.defaultConfiguration()
                .configureEmbeddedEventStore(c -> new JpaEventStorageEngine(c.serializer(), c.upcasterChain(), c.getComponent(PersistenceExceptionResolver.class), () -> em, c.getComponent(TransactionManager.class)))
                .configureAggregate(defaultConfiguration(StubAggregate.class)
                                            .configureCommandTargetResolver(c -> command -> new VersionedAggregateIdentifier(command.getPayload().toString(), null)))
                .registerEventUpcaster(c -> events -> {
                    counter.incrementAndGet();
                    return events;
                })
                .configureTransactionManager(c -> new EntityManagerTransactionManager(em))
                .buildConfiguration();
        config.start();

        config.commandGateway().sendAndWait(GenericCommandMessage.asCommandMessage("test"));
        config.commandGateway().sendAndWait(new GenericCommandMessage<>(new GenericMessage<>("test"), "update"));
        assertEquals(1, counter.get());
        assertNotNull(config.repository(StubAggregate.class));
    }

    @Test
    public void defaultConfigurationWithJpaRepository() throws Exception {
        EntityManager em = createEntityManager();
        Configuration config = DefaultConfigurer.defaultConfiguration()
                .configureTransactionManager(c -> new EntityManagerTransactionManager(em))
                .configureCommandBus(c -> {
                    AsynchronousCommandBus commandBus = new AsynchronousCommandBus();
                    commandBus.registerHandlerInterceptor(new TransactionManagingInterceptor<>(c.getComponent(TransactionManager.class)));
                    return commandBus;
                })
                .configureAggregate(
                        defaultConfiguration(StubAggregate.class)
                                .configureRepository(c -> new GenericJpaRepository<>(new SimpleEntityManagerProvider(em),
                                                                                     StubAggregate.class, c.eventBus(),
                                                                                     c.parameterResolverFactory())))
                .buildConfiguration();

        config.start();
        FutureCallback<Object, Object> callback = new FutureCallback<>();
        config.commandBus().dispatch(GenericCommandMessage.asCommandMessage("test"), callback);
        assertEquals("test", callback.get());
        assertNotNull(config.repository(StubAggregate.class));
        assertEquals(1, config.getModules().size());
    }

    @Test
    public void testRegisterSeveralModules() {
        Configuration config = DefaultConfigurer.defaultConfiguration()
                .registerModule(new EventHandlingConfiguration().usingTrackingProcessors())
                .registerModule(new EventHandlingConfiguration())
                .configureAggregate(StubAggregate.class)
                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                .start();

        assertThat(config.getModules().size(), CoreMatchers.is(3));
    }

    private EntityManager createEntityManager() {
        Map<String, String> properties = new HashMap<>();
        properties.put("hibernate.connection.url", "jdbc:hsqldb:mem:axontest");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("eventStore", properties);
        return emf.createEntityManager();
    }

    @Entity(name = "StubAggregate")
    private static class StubAggregate {

        @Id
        @AggregateIdentifier
        private String id;

        public StubAggregate() {
        }

        @CommandHandler
        public StubAggregate(String command, CommandBus commandBus) {
            apply(command);
        }

        @CommandHandler(commandName = "update")
        public void update(String command) {
            apply(1L);
        }

        @EventSourcingHandler
        protected void on(String event) {
            this.id = event;
        }
    }

    private static class EntityManagerTransactionManager implements TransactionManager {
        private final EntityManager em;

        public EntityManagerTransactionManager(EntityManager em) {
            this.em = em;
        }

        @Override
        public Transaction startTransaction() {
            EntityTransaction tx = em.getTransaction();
            if (tx.isActive()) {
                return new Transaction() {
                    @Override
                    public void commit() {
                    }

                    @Override
                    public void rollback() {
                    }
                };
            }
            tx.begin();
            return new Transaction() {
                @Override
                public void commit() {
                    tx.commit();
                }

                @Override
                public void rollback() {
                    tx.rollback();
                }
            };
        }
    }
}

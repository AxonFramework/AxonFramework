/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.legacy.jpa;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.sql.SQLException;

import static org.axonframework.eventsourcing.eventstore.EventUtils.asDomainEventMessage;

/**
 * @author Rene de Waele
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/db-context.xml")
@Transactional
public class LegacyJpaEventStorageEngineTest extends BatchingEventStorageEngineTest {

    private LegacyJpaEventStorageEngine testSubject;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    private PersistenceExceptionResolver defaultPersistenceExceptionResolver;

    @Before
    public void setUp() throws SQLException {
        defaultPersistenceExceptionResolver = new SQLErrorCodesResolver(dataSource);
        setTestSubject(
                testSubject = createEngine(NoOpEventUpcaster.INSTANCE, defaultPersistenceExceptionResolver));
    }

    @Override
    protected AbstractEventStorageEngine createEngine(EventUpcaster upcasterChain) {
        return createEngine(upcasterChain, defaultPersistenceExceptionResolver);
    }

    @Override
    protected AbstractEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver) {
        return createEngine(NoOpEventUpcaster.INSTANCE, persistenceExceptionResolver);
    }

    protected LegacyJpaEventStorageEngine createEngine(EventUpcaster upcasterChain,
                                                       PersistenceExceptionResolver persistenceExceptionResolver) {
        return new CustomLegacyJpaEventStorageEngine(upcasterChain, persistenceExceptionResolver,
                                                     new SimpleEntityManagerProvider(entityManager));
    }

    //Use custom storage engine to test because the table produced by the default LegacyDomainEventEntry table
    //conflicts with the one produced by DomainEventEntry
    private static class CustomLegacyJpaEventStorageEngine extends LegacyJpaEventStorageEngine {

        private CustomLegacyJpaEventStorageEngine(EventUpcaster upcasterChain,
                                                  PersistenceExceptionResolver persistenceExceptionResolver,
                                                  EntityManagerProvider entityManagerProvider) {
            super(new XStreamSerializer(), upcasterChain, persistenceExceptionResolver, 100, entityManagerProvider);
        }

        @Override
        protected Object createEventEntity(EventMessage<?> eventMessage, Serializer serializer) {
            return new CustomLegacyDomainEventEntry(asDomainEventMessage(eventMessage), serializer);
        }

        @Override
        protected String domainEventEntryEntityName() {
            return CustomLegacyDomainEventEntry.class.getSimpleName();
        }
    }
}

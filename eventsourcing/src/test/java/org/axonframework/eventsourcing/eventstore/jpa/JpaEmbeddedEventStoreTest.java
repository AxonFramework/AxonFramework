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

package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStoreTest;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

/**
 * An {@link EmbeddedEventStoreTest} implementation using the {@link JpaEventStorageEngine} during testing.
 *
 * @author Steven van Beelen
 */
class JpaEmbeddedEventStoreTest extends EmbeddedEventStoreTest {

    private final EntityManagerFactory emf = Persistence.createEntityManagerFactory("eventStore");
    private final EntityManager entityManager = emf.createEntityManager();
    private EntityTransaction transaction;

    @BeforeEach
    public void setUpJpa() {
        transaction = entityManager.getTransaction();
        transaction.begin();
    }

    @AfterEach
    public void rollback() {
        transaction.rollback();
    }

    @Override
    public EventStorageEngine createStorageEngine() {
        Serializer testSerializer = JacksonSerializer.defaultSerializer();
        return JpaEventStorageEngine.builder()
                                    .eventSerializer(testSerializer)
                                    .snapshotSerializer(testSerializer)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(entityManager))
                                    .transactionManager(new NoOpTransactionManager())
                                    .build();
    }
}

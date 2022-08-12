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

package org.axonframework.eventhandling.deadletter.jpa;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.axonframework.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the {@link OldJpaDeadLetterQueue} with
 * an {@link org.axonframework.eventhandling.EventProcessor} and {@link DeadLetteringEventHandlerInvoker}.
 *
 * @author Mitchell Herrijgers
 */
class JpaDeadLetteringIntegrationTest extends DeadLetteringEventIntegrationTest {

    EntityManagerFactory emf = Persistence.createEntityManagerFactory("dlq");
    EntityManager entityManager = emf.createEntityManager();

    @BeforeEach
    void setUpTransaction() {
        entityManager.getTransaction().begin();
    }

    @AfterEach
    public void rollback() {
        entityManager.getTransaction().rollback();
    }

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildDeadLetterQueue() {
        EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
        return JpaSequencedDeadLetterQueue.builder()
                                          .processingGroup(PROCESSING_GROUP)
                                          .transactionManager(transactionManager)
                                          .entityManagerProvider(entityManagerProvider)
                                          .serializer(TestSerializer.JACKSON.getSerializer())
                                          .build();
    }
}

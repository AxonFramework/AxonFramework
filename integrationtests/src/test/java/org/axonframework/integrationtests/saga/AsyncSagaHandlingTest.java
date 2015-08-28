/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.integrationtests.saga;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.annotation.AsyncAnnotatedSagaManager;
import org.axonframework.saga.repository.AbstractSagaRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/META-INF/spring/async-saga-context.xml"})
public class AsyncSagaHandlingTest {

    private static final int EVENTS_PER_SAGA = 300;
    private List<UUID> aggregateIdentifiers = new LinkedList<>();

    @Autowired
    private EventBus eventBus;

    @Autowired
    private AsyncAnnotatedSagaManager sagaManager;

    @Autowired
    private AbstractSagaRepository sagaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Before
    public void setUp() {
        assertNotNull(eventBus);
        assertNotNull(sagaRepository);
        for (int t = 0; t < 10; t++) {
            aggregateIdentifiers.add(UUID.randomUUID());
        }
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                entityManager.createQuery("DELETE FROM AssociationValueEntry e").executeUpdate();
                entityManager.createQuery("DELETE FROM SagaEntry e").executeUpdate();
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        if (sagaManager != null) {
            sagaManager.stop();
        }
    }

    @Test
    @DirtiesContext
    public void testInvokeRandomEvents() throws InterruptedException {
        for (int t = 0; t < EVENTS_PER_SAGA * aggregateIdentifiers.size(); t++) {
            eventBus.publish(new GenericEventMessage<>(new SagaStartEvent(
                    aggregateIdentifiers.get(t % aggregateIdentifiers.size()),
                    "message" + (t / aggregateIdentifiers.size()))));
        }
        sagaManager.stop();

        for (UUID id : aggregateIdentifiers) {
            validateSaga(id);
        }
    }

    @DirtiesContext
    @Test
    public void testAssociationProcessingOrder() throws InterruptedException {
        UUID currentAssociation = UUID.randomUUID();
        eventBus.publish(asEventMessage(new SagaStartEvent(currentAssociation, "message")));
        for (int t = 0; t < EVENTS_PER_SAGA; t++) {
            UUID newAssociation = UUID.randomUUID();
            eventBus.publish(asEventMessage(new SagaAssociationChangingEvent(
                    currentAssociation.toString(),
                    newAssociation.toString())));
            currentAssociation = newAssociation;
        }
        sagaManager.stop();
        Set<String> result = sagaRepository.find(AsyncSaga.class,
                                                 new AssociationValue("currentAssociation",
                                                                      currentAssociation.toString()));
        assertEquals(1, result.size());
    }

    @DirtiesContext
    @Test
    public void testStartEndAsyncSaga() throws InterruptedException {
        for (int i = 0; i < 500; i++) {
            UUID currentAssociation = UUID.randomUUID();
            eventBus.publish(asEventMessage(new SagaStartEvent(currentAssociation, "message")));
            eventBus.publish(asEventMessage(new SagaEndEvent(currentAssociation)));
        }

        sagaManager.stop();
        long associationValuesCount = (Long) entityManager.createQuery("SELECT COUNT(a) FROM AssociationValueEntry a")
                                                          .getSingleResult();
        long sagaEntryCount = (Long) entityManager.createQuery("SELECT COUNT(se) FROM SagaEntry se").getSingleResult();
        assertEquals("Did not expect any live Sagas", 0, sagaEntryCount);
        assertEquals("Did not expect any association values", 0, associationValuesCount);
    }

    private void validateSaga(UUID myId) {
        Set<String> sagas = sagaRepository.find(AsyncSaga.class, new AssociationValue("myId", myId.toString()));
        assertEquals(1, sagas.size());
        AsyncSaga saga = (AsyncSaga) sagaRepository.load(sagas.iterator().next());
        Iterator<String> messageIterator = saga.getReceivedMessages().iterator();
        for (int t = 0; t < EVENTS_PER_SAGA; t++) {
            assertEquals("Message out of order in saga " + saga.getSagaIdentifier(),
                         "message" + t,
                         messageIterator.next());
        }
    }
}

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

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.SimpleTimingSaga;
import org.axonframework.eventhandling.scheduling.StartingEvent;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.SagaRepository;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/META-INF/spring/saga-simplescheduler-integration-test.xml")
public class SimpleEventSchedulerIntegrationTest {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private SagaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ResultStoringScheduledExecutorService executorService;

    @PersistenceContext
    private EntityManager entityManager;

    @Before
    public void setUp() throws Exception {
        // the serialized form of the Saga exceeds the default length of a blob.
        // So we must alter the table to prevent data truncation
        new TransactionTemplate(transactionManager)
                .execute(new TransactionCallbackWithoutResult() {
                    @Override
                    public void doInTransactionWithoutResult(TransactionStatus status) {
                        entityManager.createNativeQuery(
                                "ALTER TABLE SagaEntry ALTER COLUMN serializedSaga VARBINARY(1024)")
                                     .executeUpdate();
                    }
                });
    }

    @Test
    public void testTimerExecution() throws InterruptedException, ExecutionException {
        final String someAssociationValue = "value";
        new TransactionTemplate(transactionManager)
                .execute(new TransactionCallbackWithoutResult() {
                    @Override
                    public void doInTransactionWithoutResult(TransactionStatus status) {
                        eventBus.publish(new GenericEventMessage<StartingEvent>(new StartingEvent(someAssociationValue)));
                    }
                });
        // the event is scheduled in 50ms, which is generally enough to get the saga committed
        Thread.sleep(150);
        Set<String> actualResult = repository.find(SimpleTimingSaga.class,
                                                   new AssociationValue("association", someAssociationValue));
        assertEquals(1, actualResult.size());
        SimpleTimingSaga saga = (SimpleTimingSaga) repository.load(actualResult.iterator().next());
        assertTrue("Expected saga to be triggered", saga.isTriggered());
        // we make sure all submitted jobs are executed successfully. get() will throw an exception if a job had failed
        for (Future<?> future : executorService.getResults()) {
            future.get();
        }
    }
}

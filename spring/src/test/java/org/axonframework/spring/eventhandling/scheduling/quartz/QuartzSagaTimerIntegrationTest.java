/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.spring.eventhandling.scheduling.quartz;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.spring.eventhandling.scheduling.SimpleTimingSaga;
import org.axonframework.spring.eventhandling.scheduling.StartingEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/META-INF/spring/saga-quartz-integration-test.xml")
public class QuartzSagaTimerIntegrationTest {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private SagaRepository<SimpleTimingSaga> repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Scheduler scheduler;

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
    public void testJobExecutesInTime() throws InterruptedException, SchedulerException {
        final AtomicReference<JobExecutionException> jobExecutionResult = new AtomicReference<>();
        final CountDownLatch jobExecutionLatch = new CountDownLatch(1);
        scheduler.getListenerManager().addJobListener(new JobListener() {
            @Override
            public String getName() {
                return "job execution result validator";
            }

            @Override
            public void jobToBeExecuted(JobExecutionContext context) {
            }

            @Override
            public void jobExecutionVetoed(JobExecutionContext context) {
            }

            @Override
            public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
                jobExecutionResult.set(jobException);
                jobExecutionLatch.countDown();
            }
        });
        assertNotNull(eventBus);
        final String randomAssociationValue = UUID.randomUUID().toString();
        EventListener listener = mock(EventListener.class);
        eventBus.subscribe(new SimpleEventProcessor("test", listener));

        new TransactionTemplate(transactionManager)
                .execute(new TransactionCallbackWithoutResult() {
                    @Override
                    public void doInTransactionWithoutResult(TransactionStatus status) {
                        eventBus.publish(new GenericEventMessage<>(new StartingEvent(randomAssociationValue)));
                        Set<String> actualResult =
                                repository.find(new AssociationValue("association", randomAssociationValue));
                        assertEquals(1, actualResult.size());
                    }
                });

        SimpleTimingSaga saga = null;
        long t1 = System.currentTimeMillis();
        while (saga == null || !saga.isTriggered()) {
            if (System.currentTimeMillis() - t1 > 1000) {
                fail("Saga not triggered within 1000 milliseconds");
            }
            Set<String> actualResult;
            actualResult = repository.find(new AssociationValue("association", randomAssociationValue));
            assertEquals(1, actualResult.size());
            saga = (SimpleTimingSaga) repository.load(actualResult.iterator().next());
        }
        assertTrue("Expected saga to be triggered", saga.isTriggered());
        assertTrue("Job did not complete within 10 seconds", jobExecutionLatch.await(10, TimeUnit.SECONDS));
        JobExecutionException jobExecutionException = jobExecutionResult.get();
        if (jobExecutionException != null) {
            throw jobExecutionException;
        }
    }
}

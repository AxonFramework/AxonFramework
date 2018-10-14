/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.utils.AssertUtils;
import org.axonframework.utils.MockException;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.junit.*;
import org.mockito.*;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class QuartzEventSchedulerTest {

    private static final String GROUP_ID = "TestGroup";

    private QuartzEventScheduler testSubject;
    private EventBus eventBus;
    private Scheduler scheduler;

    @Before
    public void setUp() throws SchedulerException {
        eventBus = mock(EventBus.class);
        SchedulerFactory schedulerFactory = new org.quartz.impl.StdSchedulerFactory();
        scheduler = schedulerFactory.getScheduler();
        scheduler.getContext().put(EventBus.class.getName(), eventBus);
        scheduler.start();
        testSubject = QuartzEventScheduler.builder()
                                          .scheduler(scheduler)
                                          .eventBus(eventBus)
                                          .build();
        testSubject.setGroupIdentifier(GROUP_ID);
    }

    @After
    public void tearDown() throws SchedulerException {
        if (scheduler != null) {
            scheduler.shutdown(true);
        }
    }

    @Test
    public void testScheduleJob() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(eventBus).publish(isA(EventMessage.class));
        ScheduleToken token = testSubject.schedule(Duration.ofMillis(30), buildTestEvent());
        assertTrue(token.toString().contains("Quartz"));
        assertTrue(token.toString().contains(GROUP_ID));
        latch.await(1, TimeUnit.SECONDS);
        verify(eventBus).publish(isA(EventMessage.class));
    }

    @Test
    public void testScheduleJobTransactionalUnitOfWork() throws InterruptedException {
        Transaction mockTransaction = mock(Transaction.class);
        final TransactionManager transactionManager = mock(TransactionManager.class);
        when(transactionManager.startTransaction()).thenReturn(mockTransaction);
        testSubject = QuartzEventScheduler.builder()
                                          .scheduler(scheduler)
                                          .eventBus(eventBus)
                                          .transactionManager(transactionManager)
                                          .build();
        testSubject.setGroupIdentifier(GROUP_ID);
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockTransaction).commit();
        ScheduleToken token = testSubject.schedule(Duration.ofMillis(30), buildTestEvent());
        assertTrue(token.toString().contains("Quartz"));
        assertTrue(token.toString().contains(GROUP_ID));
        latch.await(1, TimeUnit.SECONDS);

        AssertUtils.assertWithin(1, TimeUnit.SECONDS, () -> verify(mockTransaction).commit());
        InOrder inOrder = inOrder(transactionManager, eventBus, mockTransaction);
        inOrder.verify(transactionManager).startTransaction();
        inOrder.verify(eventBus).publish(isA(EventMessage.class));
        inOrder.verify(mockTransaction).commit();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testScheduleJobTransactionalUnitOfWorkFailingTransaction() throws InterruptedException {
        final TransactionManager transactionManager = mock(TransactionManager.class);
        final CountDownLatch latch = new CountDownLatch(1);
        when(transactionManager.startTransaction()).thenAnswer(i -> {
            latch.countDown();
            throw new MockException();
        });
        testSubject = QuartzEventScheduler.builder()
                                          .scheduler(scheduler)
                                          .eventBus(eventBus)
                                          .transactionManager(transactionManager)
                                          .build();
        testSubject.setGroupIdentifier(GROUP_ID);

        ScheduleToken token = testSubject.schedule(Duration.ofMillis(30), buildTestEvent());
        assertTrue(token.toString().contains("Quartz"));
        assertTrue(token.toString().contains(GROUP_ID));
        latch.await(1, TimeUnit.SECONDS);

        AssertUtils.assertWithin(1, TimeUnit.SECONDS, () -> verify(transactionManager).startTransaction());
        InOrder inOrder = inOrder(transactionManager, eventBus);
        inOrder.verify(transactionManager).startTransaction();
        inOrder.verifyNoMoreInteractions();

        assertFalse(CurrentUnitOfWork.isStarted());
    }

    @Test
    public void testCancelJob() throws SchedulerException {
        ScheduleToken token = testSubject.schedule(Duration.ofMillis(1000), buildTestEvent());
        assertEquals(1, scheduler.getJobKeys(GroupMatcher.groupEquals(GROUP_ID)).size());
        testSubject.cancelSchedule(token);
        assertEquals(0, scheduler.getJobKeys(GroupMatcher.groupEquals(GROUP_ID)).size());
        scheduler.shutdown(true);
        verify(eventBus, never()).publish(isA(EventMessage.class));
    }

    private EventMessage<Object> buildTestEvent() {
        return new GenericEventMessage<>(new Object());
    }
}

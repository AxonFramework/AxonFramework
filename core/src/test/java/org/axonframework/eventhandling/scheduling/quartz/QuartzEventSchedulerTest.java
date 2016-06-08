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

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.saga.Saga;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.messaging.interceptors.Transaction;
import org.axonframework.messaging.interceptors.TransactionManager;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
        SchedulerFactory schedFact = new org.quartz.impl.StdSchedulerFactory();
        testSubject = new QuartzEventScheduler();
        scheduler = schedFact.getScheduler();
        scheduler.getContext().put(EventBus.class.getName(), eventBus);
        scheduler.start();
        testSubject.setScheduler(scheduler);
        testSubject.setEventBus(eventBus);
        testSubject.setGroupIdentifier(GROUP_ID);
        testSubject.initialize();
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
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        ScheduleToken token = testSubject.schedule(Duration.ofMillis(30), new StubEvent());
        assertTrue(token.toString().contains("Quartz"));
        assertTrue(token.toString().contains(GROUP_ID));
        latch.await(1, TimeUnit.SECONDS);
        verify(eventBus).publish(isA(EventMessage.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testScheduleJob_TransactionalUnitOfWork() throws InterruptedException, SchedulerException {
        Transaction mockTransaction = mock(Transaction.class);
        final TransactionManager transactionManager = mock(TransactionManager.class);
        when(transactionManager.startTransaction()).thenReturn(mockTransaction);
        testSubject.setTransactionManager(transactionManager);
        testSubject.initialize();
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(eventBus).publish(isA(EventMessage.class));
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        ScheduleToken token = testSubject.schedule(Duration.ofMillis(30), new StubEvent());
        assertTrue(token.toString().contains("Quartz"));
        assertTrue(token.toString().contains(GROUP_ID));
        latch.await(1, TimeUnit.SECONDS);
        InOrder inOrder = inOrder(transactionManager, eventBus, mockTransaction);
        inOrder.verify(transactionManager).startTransaction();
        inOrder.verify(eventBus).publish(isA(EventMessage.class));
        inOrder.verify(mockTransaction).commit();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testScheduleJob_CustomUnitOfWork() throws InterruptedException, SchedulerException {
        final UnitOfWorkFactory unitOfWorkFactory = mock(UnitOfWorkFactory.class);
        UnitOfWork<EventMessage<?>> unitOfWork = mock(UnitOfWork.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(unitOfWork).execute(any());
        when(unitOfWorkFactory.createUnitOfWork(any())).thenReturn(unitOfWork);
        testSubject.setUnitOfWorkFactory(unitOfWorkFactory);
        testSubject.initialize();
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(eventBus).publish(isA(EventMessage.class));
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        ScheduleToken token = testSubject.schedule(Duration.ofMillis(30), new StubEvent());
        assertTrue(token.toString().contains("Quartz"));
        assertTrue(token.toString().contains(GROUP_ID));
        latch.await(1, TimeUnit.SECONDS);
        InOrder inOrder = inOrder(unitOfWorkFactory, unitOfWork, eventBus);
        inOrder.verify(unitOfWorkFactory).createUnitOfWork(any());
        inOrder.verify(unitOfWork).execute(any());
        inOrder.verify(eventBus).publish(isA(EventMessage.class));
    }

    @Test
    public void testCancelJob() throws SchedulerException, InterruptedException {
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        ScheduleToken token = testSubject.schedule(Duration.ofMillis(1000), new StubEvent());
        assertEquals(1, scheduler.getJobKeys(GroupMatcher.<JobKey>groupEquals(GROUP_ID)).size());
        testSubject.cancelSchedule(token);
        assertEquals(0, scheduler.getJobKeys(GroupMatcher.<JobKey>groupEquals(GROUP_ID)).size());
        scheduler.shutdown(true);
        verify(eventBus, never()).publish(isA(EventMessage.class));
    }

    private EventMessage newStubEvent() {
        return new GenericEventMessage<>(new StubEvent());
    }

    private class StubEvent {

    }
}

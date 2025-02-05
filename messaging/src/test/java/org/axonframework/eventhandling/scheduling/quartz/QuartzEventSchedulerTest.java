/*
 * Copyright (c) 2010-2025. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.SchedulingException;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.serialization.TestSerializer;
import org.axonframework.utils.AssertUtils;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests validating the {@link QuartzEventScheduler}.
 *
 * @author Allard Buijze
 */
class QuartzEventSchedulerTest {

    private static final String GROUP_ID = "TestGroup";

    private Scheduler scheduler;
    private EventBus eventBus;

    private QuartzEventScheduler testSubject;

    @BeforeEach
    void setUp() throws SchedulerException {
        eventBus = mock(EventBus.class);
        SchedulerFactory schedulerFactory = new org.quartz.impl.StdSchedulerFactory();
        scheduler = spy(schedulerFactory.getScheduler());
        scheduler.getContext().put(EventBus.class.getName(), eventBus);
        scheduler.start();
        testSubject = QuartzEventScheduler.builder()
                                          .scheduler(scheduler)
                                          .eventBus(eventBus)
                                          .serializer(TestSerializer.XSTREAM.getSerializer())
                                          .build();
        testSubject.setGroupIdentifier(GROUP_ID);
    }

    @AfterEach
    void tearDown() throws SchedulerException {
        if (scheduler != null) {
            scheduler.shutdown(true);
        }
    }

    @Test
    void scheduleJob() throws InterruptedException {
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
    void scheduleJobTransactionalUnitOfWork() throws InterruptedException {
        Transaction mockTransaction = mock(Transaction.class);
        final TransactionManager transactionManager = mock(TransactionManager.class);
        when(transactionManager.startTransaction()).thenReturn(mockTransaction);
        testSubject = QuartzEventScheduler.builder()
                                          .scheduler(scheduler)
                                          .eventBus(eventBus)
                                          .transactionManager(transactionManager)
                                          .serializer(TestSerializer.XSTREAM.getSerializer())
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
    void scheduleJobTransactionalUnitOfWorkFailingTransaction() throws InterruptedException {
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
                                          .serializer(TestSerializer.XSTREAM.getSerializer())
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
    void cancelJob() throws SchedulerException {
        ScheduleToken token = testSubject.schedule(Duration.ofMillis(1000), buildTestEvent());
        assertEquals(1, scheduler.getJobKeys(GroupMatcher.groupEquals(GROUP_ID)).size());
        testSubject.cancelSchedule(token);
        assertEquals(0, scheduler.getJobKeys(GroupMatcher.groupEquals(GROUP_ID)).size());
        scheduler.shutdown(true);
        verify(eventBus, never()).publish(isA(EventMessage.class));
    }

    @Test
    void shutdownInvokesSchedulerShutdown() throws SchedulerException {
        testSubject.shutdown();

        verify(scheduler).shutdown(true);
    }

    @Test
    void shutdownFailureResultsInSchedulingException() throws SchedulerException {
        Scheduler scheduler = spy(new StdSchedulerFactory().getScheduler());
        doAnswer(invocation -> {
            throw new SchedulerException();
        }).when(scheduler).shutdown(true);
        QuartzEventScheduler testSubject = QuartzEventScheduler.builder()
                                                               .scheduler(scheduler)
                                                               .eventBus(eventBus)
                                                               .serializer(TestSerializer.XSTREAM.getSerializer())
                                                               .build();

        assertThrows(SchedulingException.class, testSubject::shutdown);
    }

    @Test
    void buildWithNullEventJobDataBinderThrowsAxonConfigurationException() {
        QuartzEventScheduler.Builder builderTestSubject = QuartzEventScheduler.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.jobDataBinder(null));
    }

    @Test
    void buildWithNullSerializerThrowsAxonConfigurationException() {
        QuartzEventScheduler.Builder builderTestSubject = QuartzEventScheduler.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.serializer(null));
    }

    @Test
    void buildWithoutSchedulerThrowsAxonConfigurationException() {
        EventBus eventBus = SimpleEventBus.builder().build();
        QuartzEventScheduler.Builder builderTestSubject =
                QuartzEventScheduler.builder()
                                    .eventBus(eventBus)
                                    .serializer(TestSerializer.XSTREAM.getSerializer());

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithoutEventBusThrowsAxonConfigurationException() {
        Scheduler scheduler = mock(Scheduler.class);
        QuartzEventScheduler.Builder builderTestSubject =
                QuartzEventScheduler.builder()
                                    .scheduler(scheduler)
                                    .serializer(TestSerializer.XSTREAM.getSerializer());

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    private EventMessage<Object> buildTestEvent() {
        return new GenericEventMessage<>(new MessageType("message"), new Object());
    }
}

/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.saga.Saga;
import org.joda.time.Duration;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
import org.quartz.SchedulerException;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleEventSchedulerTest {

    private SimpleEventScheduler testSubject;
    private EventBus eventBus;
    private ScheduledExecutorService executorService;

    @Before
    public void setUp() throws SchedulerException {
        eventBus = mock(EventBus.class);
        executorService = Executors.newSingleThreadScheduledExecutor();
        testSubject = new SimpleEventScheduler(executorService, eventBus);
    }

    @After
    public void tearDown() throws SchedulerException {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    public void testScheduleJob() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                latch.countDown();
                return null;
            }
        }).when(eventBus).publish(isA(Event.class));
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        testSubject.schedule(new Duration(30), new StubEvent(mockSaga));
        latch.await(1, TimeUnit.SECONDS);
        verify(eventBus).publish(isA(StubEvent.class));
    }

    @Test
    public void testCancelJob() throws SchedulerException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                latch.countDown();
                return null;
            }
        }).when(eventBus).publish(isA(Event.class));
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        StubEvent event1 = new StubEvent(mockSaga);
        StubEvent event2 = new StubEvent(mockSaga);
        ScheduleToken token1 = testSubject.schedule(new Duration(100), event1);
        testSubject.schedule(new Duration(120), event2);
        testSubject.cancelSchedule(token1);
        latch.await(1, TimeUnit.SECONDS);
        verify(eventBus, never()).publish(event1);
        verify(eventBus).publish(event2);
        executorService.shutdown();
        assertTrue("Executor refused to shutdown within a second", executorService.awaitTermination(1,
                                                                                                    TimeUnit.SECONDS));
    }

    private class StubEvent extends ApplicationEvent {

        public StubEvent(Saga source) {
            super(source);
        }

    }
}

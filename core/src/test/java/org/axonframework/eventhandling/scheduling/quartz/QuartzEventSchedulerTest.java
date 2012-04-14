/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.saga.Saga;
import org.joda.time.Duration;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;

import java.util.UUID;
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
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                latch.countDown();
                return null;
            }
        }).when(eventBus).publish(isA(EventMessage.class));
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        ScheduleToken token = testSubject.schedule(new Duration(30), new StubEvent());
        assertTrue(token.toString().contains("Quartz"));
        assertTrue(token.toString().contains(GROUP_ID));
        latch.await(1, TimeUnit.SECONDS);
        verify(eventBus).publish(isA(EventMessage.class));
    }

    @Test
    public void testCancelJob() throws SchedulerException, InterruptedException {
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        ScheduleToken token = testSubject.schedule(new Duration(1000), newStubEvent());
        assertEquals(1, scheduler.getJobNames(GROUP_ID).length);
        testSubject.cancelSchedule(token);
        assertEquals(0, scheduler.getJobNames(GROUP_ID).length);
        scheduler.shutdown(true);
        verify(eventBus, never()).publish(isA(EventMessage.class));
    }

    private EventMessage newStubEvent() {
        return new GenericEventMessage<StubEvent>(new StubEvent());
    }

    private class StubEvent {

    }
}

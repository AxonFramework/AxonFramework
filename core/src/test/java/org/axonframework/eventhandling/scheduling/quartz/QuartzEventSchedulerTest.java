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

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.AssociationValues;
import org.axonframework.saga.Saga;
import org.axonframework.saga.annotation.AssociationValuesImpl;
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

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class QuartzEventSchedulerTest {

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
        }).when(eventBus).publish(isA(Event.class));
        Saga mockSaga = mock(Saga.class);
        when(mockSaga.getSagaIdentifier()).thenReturn(UUID.randomUUID().toString());
        testSubject.schedule(new Duration(30), new StubEvent(mockSaga));
        latch.await(1, TimeUnit.SECONDS);
        verify(eventBus).publish(isA(StubEvent.class));
    }

    private class MyStubSaga implements Saga {

        private final String id = UUID.randomUUID().toString();

        @Override
        public String getSagaIdentifier() {
            return id;
        }

        @Override
        public AssociationValues getAssociationValues() {
            return new AssociationValuesImpl();
        }

        @Override
        public void handle(Event event) {
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    private class StubEvent extends ApplicationEvent {

        public StubEvent(Saga source) {
            super(source);
        }

    }
}

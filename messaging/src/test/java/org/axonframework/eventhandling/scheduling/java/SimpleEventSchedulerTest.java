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

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests validating the {@link SimpleEventScheduler}.
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 */
@ExtendWith(MockitoExtension.class)
class SimpleEventSchedulerTest {

    private ScheduledExecutorService scheduledExecutorService;
    private EventBus eventBus;

    private SimpleEventScheduler testSubject;

    @BeforeEach
    void setUp() {
        eventBus = mock(EventBus.class);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        testSubject = SimpleEventScheduler.builder()
                                          .scheduledExecutorService(scheduledExecutorService)
                                          .eventBus(eventBus)
                                          .build();
    }

    @AfterEach
    void tearDown() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }
    }

    @Test
    void scheduleJob() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(eventBus).publish(isA(EventMessage.class));
        testSubject.schedule(Duration.ofMillis(30), new Object());
        latch.await(1, TimeUnit.SECONDS);
        verify(eventBus).publish(isA(EventMessage.class));
    }

    @Test
    void cancelJob() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(eventBus).publish(isA(EventMessage.class));
        EventMessage<Object> event1 = createEvent();
        final EventMessage<Object> event2 = createEvent();
        ScheduleToken token1 = testSubject.schedule(Duration.ofMillis(100), event1);
        testSubject.schedule(Duration.ofMillis(120), event2);
        testSubject.cancelSchedule(token1);
        latch.await(1, TimeUnit.SECONDS);
        verify(eventBus, never()).publish(event1);
        verify(eventBus).publish(argThat((ArgumentMatcher<EventMessage<Object>>) item -> (item != null)
                && event2.getPayload().equals(item.getPayload())
                && event2.getMetaData().equals(item.getMetaData())));
        scheduledExecutorService.shutdown();
        assertTrue(scheduledExecutorService.awaitTermination(1, TimeUnit.SECONDS),
                   "Executor refused to shutdown within a second");
    }

    @Test
    void shutdownInvokesExecutorServiceShutdown(@Mock ScheduledExecutorService scheduledExecutorService) {
        SimpleEventScheduler testSubject = SimpleEventScheduler.builder()
                                                               .scheduledExecutorService(scheduledExecutorService)
                                                               .eventBus(eventBus)
                                                               .build();

        testSubject.shutdown();

        verify(scheduledExecutorService).shutdown();
    }

    private EventMessage<Object> createEvent() {
        return new GenericEventMessage<>(new MessageType("event"), new Object());
    }
}

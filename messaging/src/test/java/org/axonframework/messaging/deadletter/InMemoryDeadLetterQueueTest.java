/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.messaging.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.deadletter.EventHandlingQueueIdentifier;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link InMemoryDeadLetterQueue}.
 *
 * @author Steven van Beelen
 */
class InMemoryDeadLetterQueueTest extends DeadLetterQueueTest<EventHandlingQueueIdentifier, EventMessage<?>> {

    private static final Duration EXPIRE_THRESHOLD = Duration.ofMillis(100);

    @Override
    DeadLetterQueue<EventMessage<?>> buildTestSubject() {
        return InMemoryDeadLetterQueue.<EventMessage<?>>builder()
                                      .expireThreshold(EXPIRE_THRESHOLD)
                                      .build();
    }

    @Override
    EventHandlingQueueIdentifier generateQueueId() {
        return generateQueueId(generateId());
    }

    @Override
    EventHandlingQueueIdentifier generateQueueId(String group) {
        return new EventHandlingQueueIdentifier(generateId(), group);
    }

    @Override
    EventMessage<?> generateMessage() {
        return GenericEventMessage.asEventMessage("Then this happened..." + UUID.randomUUID());
    }

    @Override
    void setClock(Clock clock) {
        InMemoryDeadLetterQueue.clock = clock;
    }

    @Override
    Duration expireThreshold() {
        return EXPIRE_THRESHOLD;
    }

    @Test
    void testMaxQueues() {
        int expectedMaxQueues = 128;

        InMemoryDeadLetterQueue<Message<?>> testSubject = InMemoryDeadLetterQueue.builder()
                                                                                 .maxQueues(expectedMaxQueues)
                                                                                 .build();

        assertEquals(expectedMaxQueues, testSubject.maxQueues());
    }

    @Test
    void testMaxQueueSize() {
        int expectedMaxQueueSize = 128;

        InMemoryDeadLetterQueue<Message<?>> testSubject = InMemoryDeadLetterQueue.builder()
                                                                                 .maxQueueSize(expectedMaxQueueSize)
                                                                                 .build();

        assertEquals(expectedMaxQueueSize, testSubject.maxQueueSize());
    }

    @Test
    void testRegisterLifecycleHandlerRegistersQueuesShutdown() {
        InMemoryDeadLetterQueue<Message<?>> testSubject = spy(InMemoryDeadLetterQueue.defaultQueue());

        AtomicInteger onStartInvoked = new AtomicInteger(0);
        AtomicInteger onShutdownInvoked = new AtomicInteger(0);

        testSubject.registerLifecycleHandlers(new Lifecycle.LifecycleRegistry() {
            @Override
            public void onStart(int phase, Lifecycle.LifecycleHandler action) {
                onStartInvoked.incrementAndGet();
            }

            @Override
            public void onShutdown(int phase, Lifecycle.LifecycleHandler action) {
                onShutdownInvoked.incrementAndGet();
                action.run();
            }
        });

        assertEquals(0, onStartInvoked.get());
        assertEquals(1, onShutdownInvoked.get());
        verify(testSubject).shutdown();
    }

    @Test
    void testShutdownReturnsCompletedFutureForCustomizedExecutor() {
        ScheduledExecutorService scheduledExecutor = spy(Executors.newScheduledThreadPool(1));
        InMemoryDeadLetterQueue<Message<?>> testSubject =
                InMemoryDeadLetterQueue.builder()
                                       .scheduledExecutorService(scheduledExecutor)
                                       .build();

        CompletableFuture<Void> result = testSubject.shutdown();

        assertTrue(result.isDone());
        verifyNoInteractions(scheduledExecutor);
    }

    @Test
    void testBuildDefaultQueue() {
        assertDoesNotThrow(() -> InMemoryDeadLetterQueue.defaultQueue());
    }

    @Test
    void testBuildWithNegativeMaxQueuesThrowsAxonConfigurationException() {
        InMemoryDeadLetterQueue.Builder<Message<?>> builderTestSubject = InMemoryDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxQueues(-1));
    }

    @Test
    void testBuildWithValueLowerThanMinimumMaxQueuesThrowsAxonConfigurationException() {
        IntStream.range(0, 127).forEach(i -> {
            InMemoryDeadLetterQueue.Builder<Message<?>> builderTestSubject = InMemoryDeadLetterQueue.builder();

            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxQueues(i));
        });
    }

    @Test
    void testBuildWithNegativeMaxQueueSizeThrowsAxonConfigurationException() {
        InMemoryDeadLetterQueue.Builder<Message<?>> builderTestSubject = InMemoryDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxQueueSize(-1));
    }

    @Test
    void testBuildWithValueLowerThanMinimumMaxQueueSizeThrowsAxonConfigurationException() {
        IntStream.range(0, 127).forEach(i -> {
            InMemoryDeadLetterQueue.Builder<Message<?>> builderTestSubject = InMemoryDeadLetterQueue.builder();

            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxQueueSize(i));
        });
    }

    @Test
    void testBuildWithNullExpireThresholdThrowsAxonConfigurationException() {
        InMemoryDeadLetterQueue.Builder<Message<?>> builderTestSubject = InMemoryDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.expireThreshold(null));
    }

    @Test
    void testBuildWithNegativeExpireThresholdThrowsAxonConfigurationException() {
        InMemoryDeadLetterQueue.Builder<Message<?>> builderTestSubject = InMemoryDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.expireThreshold(Duration.ofMillis(-1)));
    }

    @Test
    void testBuildWithZeroExpireThresholdThrowsAxonConfigurationException() {
        InMemoryDeadLetterQueue.Builder<Message<?>> builderTestSubject = InMemoryDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.expireThreshold(Duration.ZERO));
    }

    @Test
    void testBuildWithNullScheduledExecutorServiceThrowsAxonConfigurationException() {
        InMemoryDeadLetterQueue.Builder<Message<?>> builderTestSubject = InMemoryDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.scheduledExecutorService(null));
    }
}
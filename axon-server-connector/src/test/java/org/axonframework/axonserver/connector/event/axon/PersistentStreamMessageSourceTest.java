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

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.config.Configuration;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.common.Registration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistentStreamMessageSourceTest {

    private static ScheduledExecutorService TEST_SCHEDULER;
    private static ExecutorService CONCURRENT_TEST_EXECUTOR;
    private static final int THREAD_COUNT = 10;

    @Mock
    private Consumer<List<? extends EventMessage<?>>> eventConsumer;

    private PersistentStreamMessageSource messageSource;

    @BeforeAll
    static void beforeAll() {
        TEST_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
        CONCURRENT_TEST_EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @AfterAll
    static void afterAll() {
        TEST_SCHEDULER.shutdown();
        CONCURRENT_TEST_EXECUTOR.shutdown();
    }

    @BeforeEach
    void setUp() {
        String streamName = UUID.randomUUID().toString();
        Configuration configurer = DefaultConfigurer.defaultConfiguration()
                                                    .buildConfiguration();
        messageSource = new PersistentStreamMessageSource(
                streamName,
                configurer,
                new PersistentStreamProperties(streamName, 1, "example", Collections.emptyList(), "HEAD", null),
                TEST_SCHEDULER,
                1
        );
    }

    @Test
    void subscribeShouldReturnValidRegistration() {
        // when
        Registration registration = messageSource.subscribe(eventConsumer);

        // then
        assertNotNull(registration);
        assertTrue(registration.cancel());
    }

    @Test
    void subscribingTwiceWithSameConsumerShouldNotThrowException() {
        // given
        messageSource.subscribe(eventConsumer);

        // when/then
        assertDoesNotThrow(() -> messageSource.subscribe(eventConsumer));
    }

    @Test
    void subscribingWithDifferentConsumerShouldThrowException() {
        // given
        messageSource.subscribe(eventConsumer);
        Consumer<List<? extends EventMessage<?>>> anotherConsumer = mock(Consumer.class);

        // when/then
        assertThrows(IllegalStateException.class,
                     () -> messageSource.subscribe(anotherConsumer));
    }

    @Test
    void cancellingRegistrationShouldAllowNewSubscription() {
        // given
        Registration registration = messageSource.subscribe(eventConsumer);
        registration.cancel();
        Consumer<List<? extends EventMessage<?>>> newConsumer = mock(Consumer.class);

        // when/then
        assertDoesNotThrow(() -> messageSource.subscribe(newConsumer));
    }

    @Test
    void registrationCancelShouldBeIdempotent() {
        // given
        Registration registration = messageSource.subscribe(eventConsumer);

        // when
        boolean firstCancel = registration.cancel();
        boolean secondCancel = registration.cancel();

        // then
        assertTrue(firstCancel);
        assertTrue(secondCancel);
    }


    @Nested
    class ThreadSafety {

        @Test
        void concurrentSubscribeWithSameConsumerShouldBeThreadSafe() throws InterruptedException {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
            ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();

            // Create multiple threads that try to subscribe simultaneously
            IntStream.range(0, THREAD_COUNT)
                     .forEach(i -> CONCURRENT_TEST_EXECUTOR.submit(() -> {
                                  try {
                                      startLatch.await(); // Wait for all threads to be ready
                                      messageSource.subscribe(eventConsumer);
                                  } catch (Exception e) {
                                      exceptions.add(e);
                                  } finally {
                                      completionLatch.countDown();
                                  }
                              })
                     );

            // Start all threads simultaneously
            startLatch.countDown();
            completionLatch.await(5, TimeUnit.SECONDS);

            assertTrue(exceptions.isEmpty(),
                       "Concurrent subscription with same consumer should not throw exceptions: " + exceptions);
        }

        @Test
        void concurrentSubscribeWithDifferentConsumersShouldBeThreadSafe() throws InterruptedException {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
            ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();
            AtomicInteger successfulSubscriptions = new AtomicInteger(0);

            // Create multiple threads that try to subscribe with different consumers
            IntStream.range(0, THREAD_COUNT)
                     .forEach(i ->
                                      CONCURRENT_TEST_EXECUTOR.submit(() -> {
                                          try {
                                              startLatch.await();
                                              Consumer<List<? extends EventMessage<?>>> consumer = mock(
                                                      Consumer.class);
                                              messageSource.subscribe(consumer);
                                              successfulSubscriptions.incrementAndGet();
                                          } catch (IllegalStateException e) {
                                              // Expected for all but one subscription
                                          } catch (Exception e) {
                                              exceptions.add(e);
                                          } finally {
                                              completionLatch.countDown();
                                          }
                                      })
                     );

            startLatch.countDown();
            completionLatch.await(5, TimeUnit.SECONDS);

            assertTrue(exceptions.isEmpty(),
                       "Unexpected exceptions during concurrent subscription: " + exceptions);
            assertEquals(1, successfulSubscriptions.get(),
                         "Only one subscription should succeed with different consumers");
        }

        @Test
        void concurrentSubscribeAndCancelShouldBeThreadSafe() throws InterruptedException {
            int iterationCount = 100;
            CountDownLatch completionLatch = new CountDownLatch(
                    iterationCount * 2); // For both subscribe and cancel operations
            ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();

            // Create pairs of threads - one subscribing and one cancelling
            IntStream.range(0, iterationCount).forEach(i -> {
                CONCURRENT_TEST_EXECUTOR.submit(() -> {
                    try {
                        messageSource.subscribe(eventConsumer);
                        completionLatch.countDown();
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                });

                CONCURRENT_TEST_EXECUTOR.submit(() -> {
                    try {
                        Registration registration = messageSource.subscribe(eventConsumer);
                        if (registration != null) {
                            registration.cancel();
                        }
                        completionLatch.countDown();
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                });
            });

            completionLatch.await(10, TimeUnit.SECONDS);

            assertTrue(exceptions.isEmpty(),
                       "Concurrent subscribe and cancel operations should not throw exceptions: " + exceptions);
        }

        @Test
        void subscribeWhileRegistrationCancellationInProgressShouldBeThreadSafe() throws InterruptedException {
            Registration initialRegistration = messageSource.subscribe(eventConsumer);
            CountDownLatch cancellationStarted = new CountDownLatch(1);
            CountDownLatch cancellationCompleted = new CountDownLatch(1);
            CountDownLatch subscriptionAttempted = new CountDownLatch(1);

            // Thread 1: Cancel registration
            CONCURRENT_TEST_EXECUTOR.submit(() -> {
                try {
                    cancellationStarted.countDown();
                    initialRegistration.cancel();
                } finally {
                    cancellationCompleted.countDown();
                }
            });

            // Thread 2: Try to subscribe while cancellation is in progress
            CONCURRENT_TEST_EXECUTOR.submit(() -> {
                try {
                    cancellationStarted.await();
                    messageSource.subscribe(mock(Consumer.class));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    subscriptionAttempted.countDown();
                }
            });

            // Wait for both operations to complete
            assertTrue(cancellationCompleted.await(5, TimeUnit.SECONDS));
            assertTrue(subscriptionAttempted.await(5, TimeUnit.SECONDS));
        }
    }
}
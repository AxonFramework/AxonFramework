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

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.PersistentStream;
import io.axoniq.axonserver.connector.event.PersistentStreamCallbacks;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.event.PersistentStreamSegment;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.streams.PersistentStreamEvent;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.Registration;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link PersistentStreamMessageSource}.
 *
 * @author Marc Gathier
 * @author Mateusz Nowak
 */
@ExtendWith(MockitoExtension.class)
class PersistentStreamMessageSourceTest {

    private static ScheduledExecutorService TEST_SCHEDULER;
    private static ExecutorService CONCURRENT_TEST_EXECUTOR;
    private static final int THREAD_COUNT = 10;
    private static final Configuration DEFAULT_CONFIGURATION =
            MessagingConfigurer.create().componentRegistry(
                                       cr -> cr.registerComponent(AxonServerConfiguration.class, c -> new AxonServerConfiguration())
                                               .registerComponent(AxonServerConnectionManager.class, c -> {
                                                   AxonServerConfiguration serverConfig = c.getComponent(AxonServerConfiguration.class);
                                                   return AxonServerConnectionManager.builder()
                                                                                     .routingServers(serverConfig.getServers())
                                                                                     .axonServerConfiguration(serverConfig)
                                                                                     .build();
                                               })
                               )
                               .build();

    @Mock
    private BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventConsumer;

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
        messageSource = new PersistentStreamMessageSource(
                streamName,
                DEFAULT_CONFIGURATION,
                new PersistentStreamProperties(streamName, 1, "example", Collections.emptyList(), "HEAD", null),
                TEST_SCHEDULER,
                1
        );
    }

    @Nested
    class Subscribe {

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

            // when / then
            Assertions.assertDoesNotThrow(() -> messageSource.subscribe(eventConsumer));
        }

        @Test
        void subscribingWithDifferentConsumerShouldThrowException() {
            // given
            messageSource.subscribe(eventConsumer);
            BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> anotherConsumer = mock(BiFunction.class);

            // when / then
            assertThrows(IllegalStateException.class,
                         () -> messageSource.subscribe(anotherConsumer));
        }

        @Test
        void cancellingRegistrationShouldAllowNewSubscription() {
            // given
            Registration registration = messageSource.subscribe(eventConsumer);
            registration.cancel();
            BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> newConsumer = mock(BiFunction.class);

            // when / then
            Assertions.assertDoesNotThrow(() -> messageSource.subscribe(newConsumer));
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
    }

    @Nested
    class ThreadSafety {

        @Test
        void concurrentSubscribeWithSameConsumerShouldBeThreadSafe() throws InterruptedException {
            // given
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
            ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();

            // when
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

            // then
            assertTrue(exceptions.isEmpty(),
                       "Concurrent subscription with same consumer should not throw exceptions: " + exceptions);
        }

        @Test
        void concurrentSubscribeWithDifferentConsumersShouldBeThreadSafe() throws InterruptedException {
            // given
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
            ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();
            AtomicInteger successfulSubscriptions = new AtomicInteger(0);

            // when
            // Create multiple threads that try to subscribe with different consumers
            IntStream.range(0, THREAD_COUNT)
                     .forEach(i ->
                                      CONCURRENT_TEST_EXECUTOR.submit(() -> {
                                          try {
                                              startLatch.await();
                                              BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> consumer = mock(
                                                      BiFunction.class);
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

            // then
            assertTrue(exceptions.isEmpty(),
                       "Unexpected exceptions during concurrent subscription: " + exceptions);
            assertEquals(1, successfulSubscriptions.get(),
                         "Only one subscription should succeed with different consumers");
        }

        @Test
        void concurrentSubscribeAndCancelShouldBeThreadSafe() throws InterruptedException {
            // given
            int iterationCount = 100;
            CountDownLatch completionLatch = new CountDownLatch(
                    iterationCount * 2); // For both subscribe and cancel operations
            ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();

            // when
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

            // then
            assertTrue(exceptions.isEmpty(),
                       "Concurrent subscribe and cancel operations should not throw exceptions: " + exceptions);
        }

        @Test
        void subscribeWhileRegistrationCancellationInProgressShouldBeThreadSafe() throws InterruptedException {
            // given
            Registration initialRegistration = messageSource.subscribe(eventConsumer);
            CountDownLatch cancellationStarted = new CountDownLatch(1);
            CountDownLatch cancellationCompleted = new CountDownLatch(1);
            CountDownLatch subscriptionAttempted = new CountDownLatch(1);

            // when
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
                    messageSource.subscribe(mock(BiFunction.class));
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

    @Nested
    class EventCriteriaFiltering {

        private static final String STREAM_ID = "criteria-test-stream";
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        private final PersistentStreamProperties properties =
                new PersistentStreamProperties(STREAM_ID, 2, "Seq", Collections.emptyList(), "0", null);
        private final Map<String, MockPersistentStream> mockPersistentStreams = new ConcurrentHashMap<>();

        private PersistentStreamMessageSource testSubject;

        @BeforeEach
        void setUp() {
            System.setProperty("disable-axoniq-console-message", "true");
            ApplicationConfigurer configurer = MessagingConfigurer.create();
            AxonServerConnectionManager mockAxonServerConnectionManager = mock(AxonServerConnectionManager.class);
            AxonServerConnection mockAxonServerConnection = mock(AxonServerConnection.class);
            EventChannel mockEventChannel = mock(EventChannel.class);

            when(mockEventChannel.openPersistentStream(anyString(), anyInt(), anyInt(), any(), any()))
                    .thenAnswer(invocationOnMock -> {
                        String streamId = invocationOnMock.getArgument(0);
                        mockPersistentStreams.put(streamId,
                                                  new MockPersistentStream(invocationOnMock.getArgument(3)));
                        return mockPersistentStreams.get(streamId);
                    });
            when(mockAxonServerConnection.eventChannel()).thenReturn(mockEventChannel);
            when(mockAxonServerConnectionManager.getConnection(anyString())).thenReturn(mockAxonServerConnection);
            AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
            configurer.componentRegistry(
                    cr -> cr.registerComponent(AxonServerConnectionManager.class, c -> mockAxonServerConnectionManager)
                            .registerComponent(Converter.class, c -> new JacksonConverter())
                            .registerComponent(AxonServerConfiguration.class, c -> axonServerConfiguration)
            );

            testSubject = new PersistentStreamMessageSource(
                    STREAM_ID,
                    configurer.build(),
                    properties,
                    scheduler,
                    100
            );
        }

        @AfterEach
        void tearDown() {
            scheduler.shutdown();
        }

        @Test
        void filtersEventsByTypeUsingEventCriteria() {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            EventCriteria criteria = EventCriteria.havingAnyTag()
                                                  .andBeingOneOfTypes(new QualifiedName("OrderCreated"));

            // when
            testSubject.subscribe(criteria, (events, ctx) -> {
                receivedEvents.addAll(events);
                return CompletableFuture.completedFuture(null);
            });
            MockPersistentStream mockStream = mockPersistentStreams.get(STREAM_ID);
            // Publish events with different types
            mockStream.publish(0, eventWithToken(0, "OrderCreated", "order-1", "OrderAggregate", 0));
            mockStream.publish(0, eventWithToken(1, "OrderShipped", "order-1", "OrderAggregate", 1));
            mockStream.publish(0, eventWithToken(2, "OrderCreated", "order-2", "OrderAggregate", 0));

            // then
            await().atMost(Duration.ofSeconds(2))
                   .pollDelay(Duration.ofMillis(100))
                   .until(() -> receivedEvents.size() == 2);

            // Only OrderCreated events should pass the filter
            assertThat(receivedEvents).hasSize(2);
            assertThat(receivedEvents.get(0).type().qualifiedName().name()).isEqualTo("OrderCreated");
            assertThat(receivedEvents.get(1).type().qualifiedName().name()).isEqualTo("OrderCreated");
        }

        @Test
        void filtersEventsByAggregateTagsUsingEventCriteria() {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            // Filter for events from OrderAggregate with identifier "order-123"
            EventCriteria criteria = EventCriteria.havingTags("OrderAggregate", "order-123");

            // when
            testSubject.subscribe(criteria, (events, ctx) -> {
                receivedEvents.addAll(events);
                return CompletableFuture.completedFuture(null);
            });
            MockPersistentStream mockStream = mockPersistentStreams.get(STREAM_ID);
            // Publish events from different aggregates
            mockStream.publish(0, eventWithToken(0, "OrderCreated", "order-123", "OrderAggregate", 0));
            mockStream.publish(0, eventWithToken(1, "OrderCreated", "order-456", "OrderAggregate", 0));
            mockStream.publish(0, eventWithToken(2, "OrderShipped", "order-123", "OrderAggregate", 1));
            mockStream.publish(0, eventWithToken(3, "CustomerCreated", "customer-1", "CustomerAggregate", 0));

            // then
            await().atMost(Duration.ofSeconds(2))
                   .pollDelay(Duration.ofMillis(100))
                   .until(() -> receivedEvents.size() == 2);

            // Only events from OrderAggregate with id "order-123" should pass
            assertThat(receivedEvents).hasSize(2);
            assertThat(receivedEvents.get(0).type().qualifiedName().name()).isEqualTo("OrderCreated");
            assertThat(receivedEvents.get(1).type().qualifiedName().name()).isEqualTo("OrderShipped");
        }

        @Test
        void filtersEventsByAggregateTagsAndTypeUsingEventCriteria() {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            // Filter for OrderCreated events from OrderAggregate with specific identifier
            EventCriteria criteria = EventCriteria
                    .havingTags("OrderAggregate", "order-123")
                    .andBeingOneOfTypes(new QualifiedName("OrderCreated"));

            // when
            testSubject.subscribe(criteria, (events, ctx) -> {
                receivedEvents.addAll(events);
                return CompletableFuture.completedFuture(null);
            });
            MockPersistentStream mockStream = mockPersistentStreams.get(STREAM_ID);
            // Publish various events
            mockStream.publish(0, eventWithToken(0, "OrderCreated", "order-123", "OrderAggregate", 0));
            mockStream.publish(0, eventWithToken(1, "OrderShipped", "order-123", "OrderAggregate", 1));
            mockStream.publish(0, eventWithToken(2, "OrderCreated", "order-456", "OrderAggregate", 0));
            mockStream.publish(0, eventWithToken(3, "OrderCreated", "order-123", "OrderAggregate", 2));

            // then
            await().atMost(Duration.ofSeconds(2))
                   .pollDelay(Duration.ofMillis(100))
                   .until(() -> receivedEvents.size() == 2);

            // Only OrderCreated events from order-123 should pass
            assertThat(receivedEvents).hasSize(2);
            assertThat(receivedEvents).allMatch(e -> e.type().qualifiedName().name().equals("OrderCreated"));
        }

        @Test
        void doesNotReceiveEventsWhenNoneMatchCriteria() {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            AtomicInteger batchCallCount = new AtomicInteger(0);
            EventCriteria criteria = EventCriteria.havingTags("NonExistentAggregate", "non-existent-id");

            // when
            testSubject.subscribe(criteria, (events, ctx) -> {
                batchCallCount.incrementAndGet();
                receivedEvents.addAll(events);
                return CompletableFuture.completedFuture(null);
            });
            MockPersistentStream mockStream = mockPersistentStreams.get(STREAM_ID);
            mockStream.publish(0, eventWithToken(0, "OrderCreated", "order-1", "OrderAggregate", 0));
            mockStream.publish(0, eventWithToken(1, "CustomerCreated", "customer-1", "CustomerAggregate", 0));

            // then - wait a bit to ensure events are processed
            await().during(Duration.ofMillis(500))
                   .atMost(Duration.ofSeconds(1))
                   .until(() -> receivedEvents.isEmpty());

            // Consumer should never be called with events when none match
            assertThat(receivedEvents).isEmpty();
        }

        @Test
        void filtersNonAggregateEventsUsingTypeOnlyFilter() {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            // When filtering by type only, non-aggregate events should also be considered
            EventCriteria criteria = EventCriteria.havingAnyTag()
                                                  .andBeingOneOfTypes(new QualifiedName("SystemEvent"));

            // when
            testSubject.subscribe(criteria, (events, ctx) -> {
                receivedEvents.addAll(events);
                return CompletableFuture.completedFuture(null);
            });
            MockPersistentStream mockStream = mockPersistentStreams.get(STREAM_ID);
            // Publish aggregate and non-aggregate events
            mockStream.publish(0, eventWithToken(0, "OrderCreated", "order-1", "OrderAggregate", 0));
            mockStream.publish(0, eventWithTokenNoAggregate(1, "SystemEvent"));
            mockStream.publish(0, eventWithTokenNoAggregate(2, "SystemEvent"));
            mockStream.publish(0, eventWithTokenNoAggregate(3, "AnotherEvent"));

            // then
            await().atMost(Duration.ofSeconds(2))
                   .pollDelay(Duration.ofMillis(100))
                   .until(() -> receivedEvents.size() == 2);

            // Only SystemEvent types should pass
            assertThat(receivedEvents).hasSize(2);
            assertThat(receivedEvents).allMatch(e -> e.type().qualifiedName().name().equals("SystemEvent"));
        }

        @Test
        void subscribeWithHavingAnyTagAllowsAllEvents() {
            // given
            List<EventMessage> receivedEvents = new LinkedList<>();
            EventCriteria criteria = EventCriteria.havingAnyTag();

            // when
            testSubject.subscribe(criteria, (events, ctx) -> {
                receivedEvents.addAll(events);
                return CompletableFuture.completedFuture(null);
            });
            MockPersistentStream mockStream = mockPersistentStreams.get(STREAM_ID);
            mockStream.publish(0, eventWithToken(0, "OrderCreated", "order-1", "OrderAggregate", 0));
            mockStream.publish(0, eventWithTokenNoAggregate(1, "SystemEvent"));
            mockStream.publish(0, eventWithToken(2, "CustomerCreated", "customer-1", "CustomerAggregate", 0));

            // then
            await().atMost(Duration.ofSeconds(2))
                   .pollDelay(Duration.ofMillis(100))
                   .until(() -> receivedEvents.size() == 3);

            // All events should pass
            assertThat(receivedEvents).hasSize(3);
        }

        private PersistentStreamEvent eventWithToken(long token, String eventType, String aggregateId,
                                                     String aggregateType, long sequenceNumber) {
            return PersistentStreamEvent.newBuilder()
                                        .setEvent(EventWithToken.newBuilder()
                                                                .setToken(token)
                                                                .setEvent(Event.newBuilder()
                                                                               .setMessageIdentifier(UUID.randomUUID().toString())
                                                                               .setTimestamp(System.currentTimeMillis())
                                                                               .setAggregateIdentifier(aggregateId)
                                                                               .setAggregateType(aggregateType)
                                                                               .setAggregateSequenceNumber(sequenceNumber)
                                                                               .setPayload(SerializedObject.newBuilder()
                                                                                                           .setType(eventType)
                                                                                                           .setRevision("1.0")
                                                                                                           .build())
                                                                               .build())
                                                                .build())
                                        .setReplay(false)
                                        .build();
        }

        private PersistentStreamEvent eventWithTokenNoAggregate(long token, String eventType) {
            return PersistentStreamEvent.newBuilder()
                                        .setEvent(EventWithToken.newBuilder()
                                                                .setToken(token)
                                                                .setEvent(Event.newBuilder()
                                                                               .setMessageIdentifier(UUID.randomUUID().toString())
                                                                               .setTimestamp(System.currentTimeMillis())
                                                                               // No aggregate info
                                                                               .setPayload(SerializedObject.newBuilder()
                                                                                                           .setType(eventType)
                                                                                                           .setRevision("1.0")
                                                                                                           .build())
                                                                               .build())
                                                                .build())
                                        .setReplay(false)
                                        .build();
        }
    }

    /**
     * Mock implementation of {@link PersistentStream} for testing.
     */
    private static class MockPersistentStream implements PersistentStream {

        private final PersistentStreamCallbacks callbacks;
        private final Map<Integer, MockPersistentStreamSegment> segments = new ConcurrentHashMap<>();

        MockPersistentStream(PersistentStreamCallbacks callbacks) {
            this.callbacks = callbacks;
        }

        void publish(int segmentNumber, PersistentStreamEvent event) {
            @SuppressWarnings("resource")
            MockPersistentStreamSegment segment = segments.computeIfAbsent(segmentNumber, i -> {
                MockPersistentStreamSegment mockSegment = new MockPersistentStreamSegment(i);
                callbacks.onSegmentOpened().accept(mockSegment);
                mockSegment.onAvailable(() -> callbacks.onAvailable().accept(mockSegment));
                return mockSegment;
            });
            segment.publish(event);
        }

        void closeSegment(int segmentNumber) {
            MockPersistentStreamSegment segment = segments.remove(segmentNumber);
            if (segment != null) {
                callbacks.onSegmentClosed().accept(segment);
            }
        }

        long lastAcknowledged(int segmentNumber) {
            MockPersistentStreamSegment segment = segments.get(segmentNumber);
            return segment == null ? -1 : segment.lastAcknowledged.get();
        }

        @Override
        public void close() {
            callbacks.onClosed();
        }
    }

    /**
     * Mock implementation of {@link PersistentStreamSegment} for testing.
     */
    private static class MockPersistentStreamSegment implements PersistentStreamSegment {

        private final ConcurrentLinkedDeque<PersistentStreamEvent> entries = new ConcurrentLinkedDeque<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final int segment;
        private Runnable onAvailable = () -> {};
        private final AtomicLong lastAcknowledged = new AtomicLong(-1);

        MockPersistentStreamSegment(int segment) {
            this.segment = segment;
        }

        @Override
        public int segment() {
            return segment;
        }

        @Override
        public PersistentStreamEvent peek() {
            return entries.peek();
        }

        @Override
        public PersistentStreamEvent nextIfAvailable() {
            return entries.isEmpty() ? null : entries.removeFirst();
        }

        @Override
        public PersistentStreamEvent nextIfAvailable(long timeout, TimeUnit unit) throws InterruptedException {
            long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
            PersistentStreamEvent event = nextIfAvailable();
            while (event == null && System.currentTimeMillis() < endTime && !closed.get()) {
                Thread.sleep(1);
                event = nextIfAvailable();
            }
            return event;
        }

        @Override
        public PersistentStreamEvent next() throws InterruptedException {
            PersistentStreamEvent event = nextIfAvailable();
            while (event == null && !closed.get()) {
                Thread.sleep(1);
                event = nextIfAvailable();
            }
            return event;
        }

        @Override
        public void onAvailable(Runnable callback) {
            this.onAvailable = callback;
        }

        @Override
        public void close() {
            closed.set(true);
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public Optional<Throwable> getError() {
            return Optional.empty();
        }

        @Override
        public void onSegmentClosed(Runnable callback) {
            // Not required for testing
        }

        @Override
        public void acknowledge(long token) {
            lastAcknowledged.set(token);
        }

        @Override
        public void error(String error) {
            // Not required for testing
        }

        void publish(PersistentStreamEvent event) {
            entries.add(event);
            onAvailable.run();
        }
    }
}

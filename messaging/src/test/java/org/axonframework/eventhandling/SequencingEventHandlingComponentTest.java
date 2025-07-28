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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.awaitility.Awaitility;
import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Test class for {@link SequencingEventHandlingComponent} that verifies sequential processing of events with the same
 * sequence identifier while allowing concurrent processing of events with different sequence identifiers.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SequencingEventHandlingComponentTest {

    private SimpleEventHandlingComponent delegate;
    private EventHandlingComponent sequencingComponent;
    private ExecutorService executorService;
    private final AtomicReference<ProcessingContext> processingContextRef = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        delegate = new SimpleEventHandlingComponent();
        //noinspection unchecked
        sequencingComponent = new SequenceOverridingEventHandlingComponent(
                (event) -> Optional.of(asTestMessage(event).getPayload().sequenceId),
                delegate //     delegate
        );
        executorService = Executors.newFixedThreadPool(20);
    }

    EventMessage<TestPayload> asTestMessage(EventMessage<?> message) {
        //noinspection unchecked
        return (EventMessage<TestPayload>) message;
    }

    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    @Test
    void testSequentialProcessingWithSameSequenceIdentifier() {
        // Given
        List<String> executionOrder = new ArrayList<>();

        EventHandler asyncHandler = (event, context) -> {
            String eventId = asTestMessage(event).getPayload().eventId;
            CompletableFuture<Message<Void>> future = CompletableFuture.supplyAsync(() -> {
                executionOrder.add(eventId);
                return EventTestUtils.asEventMessage("sample-response");
            }, executorService);

            return MessageStream.fromFuture(future)
                                .ignoreEntries();
        };

        QualifiedName eventType = new QualifiedName(TestPayload.class);
        delegate.subscribe(eventType, asyncHandler);

        // When - Submit multiple events with the same sequence identifier concurrently
        String sameSequenceId = "sequence-1";

        var events = new ArrayList<EventMessage<?>>();
        for (int i = 1; i <= 5; i++) {
            EventMessage<?> event = testEvent(sameSequenceId, "event-" + i);
            events.add(event);
        }

        handleInUnitOfWork(events);


        // Then - Wait for all events to be processed and verify order
        Awaitility.await()
                  .atMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(executionOrder).hasSize(5));

        // Events should be processed in the order they were submitted (sequentially)
        assertThat(executionOrder)
                .hasSize(5)
                .containsExactly("event-1", "event-2", "event-3", "event-4", "event-5");
    }

    @Test
    void testSequentialProcessingWithMixedSequenceIdentifiers() {
        // Given
        List<String> executionOrder = new CopyOnWriteArrayList<>();

        EventHandler asyncHandler = (event, context) -> {
            String eventString = asTestMessage(event).getPayload().toString();
            // todo: too fast?
            CompletableFuture<Message<Void>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(10);
                    executionOrder.add(eventString);
                    return EventTestUtils.asEventMessage("sample-response");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, executorService);

            return MessageStream.fromFuture(future)
                                .ignoreEntries();
        };

        QualifiedName eventType = new QualifiedName(TestPayload.class);
        delegate.subscribe(eventType, asyncHandler);

        // When - Submit events with alternating sequence identifiers
        String[] sequenceIds = {"seq-A", "seq-B", "seq-A", "seq-C", "seq-B", "seq-A", "seq-C"};

        var events = new ArrayList<EventMessage<?>>();
        for (int i = 0; i < sequenceIds.length; i++) {
            final String sequenceId = sequenceIds[i];
            EventMessage<?> event = testEvent(sequenceId, "event" + (i + 1));
            events.add(event);
        }
        handleInUnitOfWork(events);


        // Then - Wait for all events to be processed
        Awaitility.await()
                  .atMost(Duration.ofSeconds(10))
                  .untilAsserted(() -> assertThat(executionOrder).hasSameSizeAs(sequenceIds));

        assertThat(executionOrder).hasSize(sequenceIds.length);

        // Verify sequential ordering within each sequence group
        List<String> seqAEvents = executionOrder.stream()
                                                .filter(event -> event.startsWith("seq-A"))
                                                .toList();

        List<String> seqBEvents = executionOrder.stream()
                                                .filter(event -> event.startsWith("seq-B"))
                                                .toList();

        List<String> seqCEvents = executionOrder.stream()
                                                .filter(event -> event.startsWith("seq-C"))
                                                .toList();

        // Events within the same sequence should maintain their submission order
        assertThat(seqAEvents)
                .as("seq-A events should maintain order")
                .containsExactly("seq-A-event1", "seq-A-event3", "seq-A-event6");

        assertThat(seqBEvents)
                .as("seq-B events should maintain order")
                .containsExactly("seq-B-event2", "seq-B-event5");

        assertThat(seqCEvents)
                .as("seq-C events should maintain order")
                .containsExactly("seq-C-event4", "seq-C-event7");
    }

    private void handleInUnitOfWork(List<EventMessage<?>> events) {
        var unitOfWork = new SimpleUnitOfWorkFactory().create();
        var components = new ProcessorEventHandlingComponents(sequencingComponent);
        unitOfWork.onInvocation(ctx -> components.handle(events, ctx).asCompletableFuture());
        FutureUtils.joinAndUnwrap(unitOfWork.execute());
    }

    @Test
    void testConcurrentProcessingWithDifferentSequenceIdentifiers() {
        // Given
        List<String> executionOrder = new CopyOnWriteArrayList<>();
        AtomicInteger processedCount = new AtomicInteger(0);

        EventHandler asyncHandler = (event, context) -> {
            CompletableFuture<Message<Void>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Add delay to make threading effects visible
                    Thread.sleep(ThreadLocalRandom.current().nextInt(10, 30));

                    String eventId = event.getPayload().toString();
                    executionOrder.add(eventId);
                    processedCount.incrementAndGet();
                    return null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }, executorService);

            return MessageStream.fromFuture(future).ignoreEntries();
        };

        QualifiedName eventType = new QualifiedName("test.Event");
        delegate.subscribe(eventType, asyncHandler);

        // When - Submit events with different sequence identifiers
        // Events with sequence-1 (should be sequential)
        CompletableFuture.runAsync(() -> {
            EventMessage<?> event = testEvent("sequence-1");
            sequencingComponent.handle(event, processingContextRef.get());
        }, executorService);

        CompletableFuture.runAsync(() -> {
            EventMessage<?> event = testEvent("sequence-1");
            sequencingComponent.handle(event, processingContextRef.get());
        }, executorService);

        // Events with sequence-2 (should be sequential within this group)
        CompletableFuture.runAsync(() -> {
            EventMessage<?> event = testEvent("sequence-2");
            sequencingComponent.handle(event, processingContextRef.get());
        }, executorService);

        CompletableFuture.runAsync(() -> {
            EventMessage<?> event = testEvent("sequence-2");
            sequencingComponent.handle(event, processingContextRef.get());
        }, executorService);

        // Events with sequence-3
        CompletableFuture.runAsync(() -> {
            EventMessage<?> event = testEvent("sequence-3");
            sequencingComponent.handle(event, processingContextRef.get());
        }, executorService);

        CompletableFuture.runAsync(() -> {
            EventMessage<?> event = testEvent("sequence-3");
            sequencingComponent.handle(event, processingContextRef.get());
        }, executorService);

        // Then - Wait for all events to be processed
        Awaitility.await()
                  .atMost(Duration.ofSeconds(10))
                  .until(() -> processedCount.get() == 6);

        // Verify sequential ordering within each sequence group
        assertThat(executionOrder).hasSize(6);

        // Find positions of events for each sequence
        int seq1Event1Pos = executionOrder.indexOf("seq1-event1");
        int seq1Event2Pos = executionOrder.indexOf("seq1-event2");
        int seq2Event1Pos = executionOrder.indexOf("seq2-event1");
        int seq2Event2Pos = executionOrder.indexOf("seq2-event2");
        int seq3Event1Pos = executionOrder.indexOf("seq3-event1");
        int seq3Event2Pos = executionOrder.indexOf("seq3-event2");

        // Events within the same sequence should maintain order
        assertThat(seq1Event1Pos)
                .as("seq1-event1 should be processed before seq1-event2")
                .isLessThan(seq1Event2Pos);

        assertThat(seq2Event1Pos)
                .as("seq2-event1 should be processed before seq2-event2")
                .isLessThan(seq2Event2Pos);

        assertThat(seq3Event1Pos)
                .as("seq3-event1 should be processed before seq3-event2")
                .isLessThan(seq3Event2Pos);
    }

    @Test
    void testEmptyStreamHandling() {
        // Given
        EventHandler emptyHandler = (event, context) -> MessageStream.empty();
        QualifiedName eventType = new QualifiedName(TestPayload.class);
        delegate.subscribe(eventType, emptyHandler);

        // When & Then - Should complete without issues
        assertThatNoException().isThrownBy(() -> {
            EventMessage<?> event = testEvent("sequence-1");
            MessageStream.Empty<Message<Void>> result = sequencingComponent.handle(event, processingContextRef.get());

            assertThat(result).isNotNull();
            assertThat(result.isCompleted()).isTrue();
        });
    }

    @Test
    void testErrorPropagation() {
        // Given
        RuntimeException testException = new RuntimeException("Test exception");
        EventHandler failingHandler = (event, context) -> MessageStream.failed(testException);

        QualifiedName eventType = new QualifiedName(TestPayload.class);
        delegate.subscribe(eventType, failingHandler);

        // When
        EventMessage<?> event = testEvent("sequence-1");
        MessageStream.Empty<Message<Void>> result = sequencingComponent.handle(event, processingContextRef.get());

        // Then - Error should be propagated
        assertThat(result.error())
                .isPresent()
                .hasValue(testException);
    }

    private EventMessage<?> testEvent(String sequenceId) {
        return EventTestUtils.asEventMessage(new TestPayload(sequenceId, sequenceId));
    }


    private EventMessage<?> testEvent(String sequenceId, String payload) {
        return EventTestUtils.asEventMessage(new TestPayload(sequenceId, payload));
    }

    record TestPayload(String sequenceId, String eventId) {

        @Nonnull
        @Override
        public String toString() {
            return sequenceId + "-" + eventId;
        }
    }
}

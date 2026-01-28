/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetteringEventHandlingComponent;
import org.axonframework.messaging.eventhandling.replay.ResetContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class validating the ordering behavior when DLQ processing and normal event handling
 * happen concurrently for the same sequence.
 * <p>
 * This test validates that ordering guarantees are maintained when:
 * <ol>
 *     <li>A dead letter (E1) is being processed via {@code process()}</li>
 *     <li>A new event (E2) for the same sequence arrives via normal processing</li>
 *     <li>Both happen concurrently on different threads</li>
 * </ol>
 * <p>
 * <b>Expected Behavior:</b>
 * <p>
 * When E2 arrives while E1 is being processed:
 * <ol>
 *     <li>E2's {@code contains()} check sees E1 in the DLQ</li>
 *     <li>E2 is parked (enqueued) to the DLQ to maintain ordering</li>
 *     <li>After E1 is successfully processed and evicted, the DLQ processing loop continues</li>
 *     <li>E2 is picked up from the DLQ and processed</li>
 *     <li>Final order: E1 → E2 (ordering preserved)</li>
 * </ol>
 * <p>
 * <b>Note on TOCTOU Protection:</b>
 * <p>
 * {@link DeadLetteringEventHandlingComponent#handle} includes defensive handling for the race condition
 * where the sequence might be evicted between the {@code contains()} check and the {@code enqueueIfPresent()}
 * call. If {@code enqueueIfPresent()} returns false (sequence no longer present), the event is handled
 * normally through the delegate rather than being lost.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
class ProcessorEventHandlingComponentsDlqOrderingTest {

    private RecordingEventHandlingComponent recordingHandler;
    private SequencedDeadLetterQueue<EventMessage> deadLetterQueue;
    private DeadLetteringEventHandlingComponent deadLetteringComponent;
    private ProcessorEventHandlingComponents processorComponents;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        recordingHandler = new RecordingEventHandlingComponent();
        deadLetterQueue = InMemorySequencedDeadLetterQueue.<EventMessage>builder().build();
        EnqueuePolicy<EventMessage> enqueuePolicy = (letter, cause) -> Decisions.enqueue(cause);

        deadLetteringComponent = new DeadLetteringEventHandlingComponent(
                recordingHandler, deadLetterQueue, enqueuePolicy, true
        );

        // ProcessorEventHandlingComponents wraps in SequencingEventHandlingComponent
        processorComponents = new ProcessorEventHandlingComponents(List.of(deadLetteringComponent));
    }

    @Nested
    class WhenProcessingDlqAndNewEventsConcurrently {

        @RepeatedTest(10)
        void orderingBehaviorWhenProcessingDlqAndNewEventsConcurrently() throws InterruptedException {
            // given
            String sequenceId = "test-sequence";

            // E1 is in DLQ (failed earlier)
            EventMessage e1 = createEvent(sequenceId, "E1");
            deadLetterQueue.enqueue(sequenceId, new GenericDeadLetter<>(sequenceId, e1)).join();

            // Synchronization latches
            CountDownLatch e1StartedProcessing = new CountDownLatch(1);
            CountDownLatch e2StartedProcessing = new CountDownLatch(1); // E2 actually started (not just submitted)
            CountDownLatch e1CanFinish = new CountDownLatch(1);

            // E1 handler: signal start, wait for E2 to actually start processing, then wait to finish
            recordingHandler.onHandle("E1", () -> {
                e1StartedProcessing.countDown();
                await(e2StartedProcessing); // Wait for E2 to actually start processing
                await(e1CanFinish);
            });

            // E2 handler: signal that E2 reached the handler (if not parked)
            CountDownLatch e2ReachedHandler = new CountDownLatch(1);
            recordingHandler.onHandle("E2", e2ReachedHandler::countDown);

            // when
            // Thread B: Start DLQ processing (will pick up E1)
            CompletableFuture<Boolean> processResult = CompletableFuture.supplyAsync(() ->
                    deadLetteringComponent.processAny(new StubProcessingContext()).join()
            );

            // Wait until E1 handler starts
            assertTrue(e1StartedProcessing.await(1, TimeUnit.SECONDS), "E1 should start processing");

            // Thread A: New event E2 arrives while E1 is being processed
            EventMessage e2 = createEvent(sequenceId, "E2");
            CompletableFuture<Void> e2Result = CompletableFuture.runAsync(() -> {
                e2StartedProcessing.countDown(); // Signal E2 processing has started
                processorComponents.handle(List.of(e2), new StubProcessingContext())
                                   .asCompletableFuture().join();
            });

            // Check if E2 reached handler while E1 is still processing
            // (E1 is blocked waiting for e1CanFinish)
            boolean e2ProcessedDuringE1 = e2ReachedHandler.await(200, TimeUnit.MILLISECONDS);

            // Let E1 finish
            e1CanFinish.countDown();

            // Wait for both to complete
            processResult.join();
            e2Result.join();

            // then
            List<String> handledOrder = recordingHandler.handledEvents();
            boolean dlqContainsSequence = deadLetterQueue.contains(sequenceId).join();
            long dlqSequenceSize = deadLetterQueue.sequenceSize(sequenceId).join();

            // then
            // E2 should be handled after E1 - ordering preserved
            assertThat(handledOrder).containsExactly("E1", "E2");

            // DLQ should be empty - both events processed successfully
            assertThat(dlqContainsSequence).isFalse();
            assertThat(dlqSequenceSize).isZero();

            // E2 should NOT have reached handler while E1 was processing
            // (it was correctly parked in DLQ)
            assertThat(e2ProcessedDuringE1)
                    .as("E2 should be parked while E1 is processing")
                    .isFalse();
        }
    }

    private EventMessage createEvent(String sequenceId, String eventId) {
        return EventTestUtils.asEventMessage(new TestPayload(sequenceId, eventId));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * Simple payload with sequence ID and event ID for testing.
     */
    private record TestPayload(String sequenceId, String eventId) {
    }

    /**
     * An {@link EventHandlingComponent} that records handled events and supports hooks for test synchronization.
     */
    private static class RecordingEventHandlingComponent implements EventHandlingComponent {

        private final List<String> handledEventIds = new CopyOnWriteArrayList<>();
        private final Map<String, Runnable> handleHooks = new ConcurrentHashMap<>();
        private final Set<String> failingEventIds = ConcurrentHashMap.newKeySet();

        /**
         * Registers a hook to be executed when the specified event is handled.
         * Useful for test synchronization.
         */
        void onHandle(String eventId, Runnable hook) {
            handleHooks.put(eventId, hook);
        }

        /**
         * Configures the handler to fail when processing the specified event.
         */
        void failOn(String eventId) {
            failingEventIds.add(eventId);
        }

        /**
         * Returns the list of handled event IDs in order.
         */
        List<String> handledEvents() {
            return new ArrayList<>(handledEventIds);
        }

        @Nonnull
        @Override
        public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                                   @Nonnull ProcessingContext context) {
            TestPayload payload = (TestPayload) event.payload();
            String eventId = payload.eventId();

            // Execute hook if present (for synchronization in tests)
            Runnable hook = handleHooks.get(eventId);
            if (hook != null) {
                hook.run();
            }

            handledEventIds.add(eventId);

            if (failingEventIds.contains(eventId)) {
                return MessageStream.failed(new RuntimeException("Configured to fail: " + eventId))
                                    .ignoreEntries();
            }
            return MessageStream.empty();
        }

        @Nonnull
        @Override
        public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            return ((TestPayload) event.payload()).sequenceId();
        }

        @Nonnull
        @Override
        public MessageStream.Empty<Message> handle(@Nonnull ResetContext resetContext,
                                                   @Nonnull ProcessingContext context) {
            return MessageStream.empty();
        }

        @Nonnull
        @Override
        public Set<QualifiedName> supportedEvents() {
            // Support all events for testing
            return Collections.emptySet();
        }

        @Override
        public boolean supports(@Nonnull QualifiedName eventName) {
            // Accept all events for testing
            return true;
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            // not needed for tests
        }
    }
}

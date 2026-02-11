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

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.replay.GenericResetContext;
import org.axonframework.messaging.eventhandling.replay.ResetContext;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.annotation.Nonnull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link DeadLetteringEventHandlingComponent}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DeadLetteringEventHandlingComponentTest {

    private static final String TEST_SEQUENCE_ID = "test-sequence-id";

    private SequencedDeadLetterQueue<EventMessage> queue;
    private StubEventHandlingComponent delegate;
    private EnqueuePolicy<EventMessage> enqueuePolicy;
    private UnitOfWorkFactory unitOfWorkFactory;

    private DeadLetteringEventHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.systemDefaultZone();
        queue = InMemorySequencedDeadLetterQueue.<EventMessage>builder().build();
        delegate = new StubEventHandlingComponent(TEST_SEQUENCE_ID);
        enqueuePolicy = (letter, cause) -> Decisions.enqueue(cause);
        unitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);

        testSubject = new DeadLetteringEventHandlingComponent(delegate, queue, enqueuePolicy, unitOfWorkFactory, true);
    }

    @Nested
    class WhenHandlingEvents {

        @Test
        void delegatesToWrappedComponentForNormalProcessing() {
            // given
            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

            // then
            assertSuccessfulStream(result);
            assertTrue(delegate.wasHandled());
            assertThat(delegate.handledEvent()).isEqualTo(testEvent);
            assertFalse(queue.contains(TEST_SEQUENCE_ID).join());
        }

        @Test
        void enqueuesFollowUpWhenSequenceAlreadyInQueue() {
            // given
            GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

            EventMessage firstEvent = EventTestUtils.asEventMessage("first-payload");
            DeadLetter<EventMessage> firstLetter = new GenericDeadLetter<>(TEST_SEQUENCE_ID, firstEvent);
            queue.enqueue(TEST_SEQUENCE_ID, firstLetter).join();

            EventMessage secondEvent = EventTestUtils.asEventMessage("second-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(secondEvent, context);

            // then - delegate should NOT be called because sequence is already dead-lettered
            assertSuccessfulStream(result);
            assertFalse(delegate.wasHandled());
            assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isEqualTo(2);
        }

        @Test
        void enqueuesWhenDelegateThrowsAndPolicyDecidesToEnqueue() {
            // given
            GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

            RuntimeException testException = new RuntimeException("test failure");
            delegate.failingWith(testException);

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

            // then
            assertSuccessfulStream(result);
            assertTrue(delegate.wasHandled());
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());
            assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isEqualTo(1);
        }

        @Test
        void enqueuesWhenPolicyDecidesToIgnore() {
            // given - Ignore means "use default behavior" which is to enqueue
            testSubject = new DeadLetteringEventHandlingComponent(
                    delegate, queue, (letter, cause) -> Decisions.ignore(), unitOfWorkFactory, true
            );

            RuntimeException testException = new RuntimeException("test failure");
            delegate.failingWith(testException);

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

            // then
            assertSuccessfulStream(result);
            assertTrue(delegate.wasHandled());
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());
        }

        @Test
        void doesNotEnqueueWhenPolicyDecidesToDoNotEnqueue() {
            // given
            testSubject = new DeadLetteringEventHandlingComponent(
                    delegate, queue, (letter, cause) -> Decisions.doNotEnqueue(), unitOfWorkFactory, true
            );

            RuntimeException testException = new RuntimeException("test failure");
            delegate.failingWith(testException);

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

            // then
            assertSuccessfulStream(result);
            assertTrue(delegate.wasHandled());
            assertFalse(queue.contains(TEST_SEQUENCE_ID).join());
        }

        @Test
        @SuppressWarnings("unchecked")
        void propagatesErrorWhenEnqueueFails() {
            // given
            RuntimeException enqueueException = new RuntimeException("queue failure");
            SequencedDeadLetterQueue<EventMessage> failingQueue = mock(SequencedDeadLetterQueue.class);
            when(failingQueue.enqueueIfPresent(any(), any())).thenReturn(CompletableFuture.completedFuture(false));
            when(failingQueue.enqueue(any(), any())).thenReturn(CompletableFuture.failedFuture(enqueueException));

            testSubject = new DeadLetteringEventHandlingComponent(
                    delegate, failingQueue, enqueuePolicy, unitOfWorkFactory, true
            );

            RuntimeException handlerException = new RuntimeException("handler failure");
            delegate.failingWith(handlerException);

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

            // then - the enqueue failure should propagate
            assertFailedStreamWithError(result, RuntimeException.class, "queue failure");
        }

        @Test
        @SuppressWarnings("unchecked")
        void propagatesErrorWhenEnqueueIfPresentFails() {
            // given
            RuntimeException enqueueException = new RuntimeException("queue failure");
            SequencedDeadLetterQueue<EventMessage> failingQueue = mock(SequencedDeadLetterQueue.class);
            when(failingQueue.enqueueIfPresent(any(),
                                               any())).thenReturn(CompletableFuture.failedFuture(enqueueException));

            testSubject = new DeadLetteringEventHandlingComponent(
                    delegate, failingQueue, enqueuePolicy, unitOfWorkFactory, true
            );

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

            // then - the enqueueIfPresent failure should propagate
            assertFailedStreamWithError(result, RuntimeException.class, "queue failure");
        }
    }

    @Nested
    class WhenResetting {

        @Test
        void clearsQueueWhenAllowResetIsTrue() {
            // given
            testSubject = new DeadLetteringEventHandlingComponent(
                    delegate, queue, enqueuePolicy, unitOfWorkFactory, true
            );

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());

            ResetContext resetContext = new GenericResetContext(new MessageType(String.class), "reset-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(resetContext, context);

            // then
            assertSuccessfulStream(result);
            assertFalse(queue.contains(TEST_SEQUENCE_ID).join());
            assertThat(queue.size().join()).isEqualTo(0L);
        }

        @Test
        void doesNotClearQueueWhenAllowResetIsFalse() {
            // given
            testSubject = new DeadLetteringEventHandlingComponent(
                    delegate, queue, enqueuePolicy, unitOfWorkFactory, false
            );

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());

            ResetContext resetContext = new GenericResetContext(new MessageType(String.class), "reset-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(resetContext, context);

            // then - queue should NOT be cleared
            assertSuccessfulStream(result);
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());
            assertThat(queue.size().join()).isEqualTo(1L);
        }
    }

    @Nested
    class WhenProcessingDeadLetters {

        @Test
        void processAnyReturnsFalseWhenQueueIsEmpty() {
            // when
            boolean result = testSubject.processAny().join();

            // then
            assertFalse(result);
        }

        @Test
        void processAnyReturnsTrueAndEvictsSuccessfulLetter() {
            // given
            GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());

            // when
            boolean result = testSubject.processAny().join();

            // then
            assertTrue(result);
            assertFalse(queue.contains(TEST_SEQUENCE_ID).join());
            assertTrue(delegate.wasHandled());
        }

        @Test
        void processWithFilterProcessesMatchingSequenceOnly() {
            // given
            GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

            String otherSequenceId = "other-sequence-id";
            EventMessage testEvent1 = EventTestUtils.asEventMessage("payload-1");
            EventMessage testEvent2 = EventTestUtils.asEventMessage("payload-2");

            // Create delegate that returns different sequence IDs based on event
            delegate = new StubEventHandlingComponent(TEST_SEQUENCE_ID) {
                @Nonnull
                @Override
                public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
                    if (event.payload().toString().contains("payload-1")) {
                        return TEST_SEQUENCE_ID;
                    }
                    return otherSequenceId;
                }
            };
            testSubject = new DeadLetteringEventHandlingComponent(
                    delegate, queue, enqueuePolicy, unitOfWorkFactory, true
            );

            queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent1)).join();
            queue.enqueue(otherSequenceId, new GenericDeadLetter<>(otherSequenceId, testEvent2)).join();

            // when - process only letters matching TEST_SEQUENCE_ID
            boolean result = testSubject.process(
                    letter -> letter.message().payload().toString().contains("payload-1")
            ).join();

            // then
            assertTrue(result);
            assertFalse(queue.contains(TEST_SEQUENCE_ID).join());
            assertTrue(queue.contains(otherSequenceId).join()); // other sequence not processed
        }

        @Test
        void processRequeuesLetterWhenHandlingFails() {
            // given
            GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();

            // Configure delegate to fail
            RuntimeException testException = new RuntimeException("processing failed");
            delegate.failingWith(testException);

            // when
            boolean result = testSubject.processAny().join();

            // then - letter should be requeued (still in queue)
            assertFalse(result);
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());
            assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isEqualTo(1);
        }
    }

    @Nested
    class WhenUsingDifferentEnqueuePolicies {

        @Nested
        class DuringEventHandling {

            @Test
            void enqueuePolicyWithoutCausePreservesOriginalCause() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                // Decisions.enqueue() without cause means "use the original error from the dead letter"
                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, (letter, cause) -> Decisions.enqueue(), unitOfWorkFactory, true
                );

                RuntimeException testException = new RuntimeException("original failure");
                delegate.failingWith(testException);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then - the original error is preserved since no cause was provided by the policy
                assertSuccessfulStream(result);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("test-payload");
                assertThat(deadLetter.cause()).hasValueSatisfying(
                        cause -> assertThat(cause.message()).contains("original failure")
                );
            }

            @Test
            void enqueuePolicyWithCauseEnqueuesLetterWithCause() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, (letter, cause) -> Decisions.enqueue(cause), unitOfWorkFactory, true
                );

                RuntimeException testException = new RuntimeException("specific failure reason");
                delegate.failingWith(testException);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then
                assertSuccessfulStream(result);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("test-payload");
                assertThat(deadLetter.cause()).hasValueSatisfying(
                        cause -> assertThat(cause.message()).contains("specific failure reason")
                );
            }

            @Test
            void enqueuePolicyWithCustomDiagnosticsEnqueuesWithDiagnostics() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue,
                        (letter, cause) -> Decisions.enqueue(
                                cause,
                                l -> l.diagnostics().and("custom-key", "custom-value")
                        ),
                        unitOfWorkFactory, true
                );

                RuntimeException testException = new RuntimeException("test failure");
                delegate.failingWith(testException);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then
                assertSuccessfulStream(result);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("test-payload");
                assertThat(deadLetter.diagnostics().get("custom-key")).isEqualTo("custom-value");
            }

            @Test
            void evictDecisionDoesNotEnqueue() {
                // given
                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, (letter, cause) -> Decisions.evict(), unitOfWorkFactory, true
                );

                RuntimeException testException = new RuntimeException("test failure");
                delegate.failingWith(testException);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then
                assertSuccessfulStream(result);
                assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isZero();
            }

            @Test
            void policyBasedOnExceptionTypeEnqueuesOnlyForSpecificExceptions() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                EnqueuePolicy<EventMessage> exceptionTypePolicy = (letter, cause) -> {
                    if (cause instanceof IllegalArgumentException) {
                        return Decisions.enqueue(cause);
                    }
                    return Decisions.doNotEnqueue();
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, exceptionTypePolicy, unitOfWorkFactory, true
                );

                IllegalArgumentException expectedEnqueue = new IllegalArgumentException("should enqueue");
                delegate.failingWith(expectedEnqueue);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then
                assertSuccessfulStream(result);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("test-payload");
                assertThat(deadLetter.cause()).hasValueSatisfying(
                        cause -> assertThat(cause.type()).contains("IllegalArgumentException")
                );
            }

            @Test
            void policyBasedOnExceptionTypeDoesNotEnqueueForOtherExceptions() {
                // given
                EnqueuePolicy<EventMessage> exceptionTypePolicy = (letter, cause) -> {
                    if (cause instanceof IllegalArgumentException) {
                        return Decisions.enqueue(cause);
                    }
                    return Decisions.doNotEnqueue();
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, exceptionTypePolicy, unitOfWorkFactory, true
                );

                RuntimeException notExpectedEnqueue = new RuntimeException("should not enqueue");
                delegate.failingWith(notExpectedEnqueue);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then
                assertSuccessfulStream(result);
                assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isZero();
            }

            @Test
            void policyCanInspectDeadLetterMessageAndEnqueue() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                EnqueuePolicy<EventMessage> messageInspectingPolicy = (letter, cause) -> {
                    Object payload = letter.message().payload();
                    if (payload != null && payload.toString().contains("important")) {
                        return Decisions.enqueue(cause);
                    }
                    return Decisions.doNotEnqueue();
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, messageInspectingPolicy, unitOfWorkFactory, true
                );

                RuntimeException testException = new RuntimeException("test failure");
                delegate.failingWith(testException);

                EventMessage importantEvent = EventTestUtils.asEventMessage("important-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(importantEvent, context);

                // then
                assertSuccessfulStream(result);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("important-payload");
            }

            @Test
            void policyCanInspectDeadLetterMessageAndRejectNonImportant() {
                // given
                EnqueuePolicy<EventMessage> messageInspectingPolicy = (letter, cause) -> {
                    Object payload = letter.message().payload();
                    if (payload != null && payload.toString().contains("important")) {
                        return Decisions.enqueue(cause);
                    }
                    return Decisions.doNotEnqueue();
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, messageInspectingPolicy, unitOfWorkFactory, true
                );

                RuntimeException testException = new RuntimeException("test failure");
                delegate.failingWith(testException);

                EventMessage regularEvent = EventTestUtils.asEventMessage("regular-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(regularEvent, context);

                // then
                assertSuccessfulStream(result);
                assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isZero();
            }
        }

        @Nested
        class DuringDeadLetterProcessing {

            @Test
            void processEvictsLetterWhenPolicyReturnsDoNotEnqueueOnFailure() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                EnqueuePolicy<EventMessage> evictOnFailurePolicy = (letter, cause) -> Decisions.doNotEnqueue();

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, evictOnFailurePolicy, unitOfWorkFactory, true
                );

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();

                RuntimeException testException = new RuntimeException("processing failed");
                delegate.failingWith(testException);

                // when
                boolean result = testSubject.processAny().join();

                // then - letter should be evicted despite failure
                assertTrue(result);
                assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isZero();
            }

            @Test
            void processEvictsLetterWhenPolicyReturnsEvictOnFailure() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                EnqueuePolicy<EventMessage> evictPolicy = (letter, cause) -> Decisions.evict();

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, evictPolicy, unitOfWorkFactory, true
                );

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();

                RuntimeException testException = new RuntimeException("processing failed");
                delegate.failingWith(testException);

                // when
                boolean result = testSubject.processAny().join();

                // then - letter should be evicted despite failure
                assertTrue(result);
                assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isZero();
            }

            @Test
            void processRequeuesLetterWhenPolicyReturnsRequeueAndPreservesCause() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                EnqueuePolicy<EventMessage> requeuePolicy = (letter, cause) -> Decisions.requeue(cause);

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, requeuePolicy, unitOfWorkFactory, true
                );

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();

                RuntimeException testException = new RuntimeException("processing failed");
                delegate.failingWith(testException);

                // when
                boolean result = testSubject.processAny().join();

                // then - letter should remain in queue with updated cause
                assertFalse(result);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("test-payload");
                assertThat(deadLetter.cause()).hasValueSatisfying(
                        cause -> assertThat(cause.message()).contains("processing failed")
                );
            }

            @Test
            void successfulProcessingEvictsLetterRegardlessOfPolicy() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                EnqueuePolicy<EventMessage> alwaysRequeuePolicy = (letter, cause) -> Decisions.requeue(cause);

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, alwaysRequeuePolicy, unitOfWorkFactory, true
                );

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();

                // when - delegate does not fail, so processing succeeds
                boolean result = testSubject.processAny().join();

                // then - letter should be evicted on success
                assertTrue(result);
                assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isZero();
            }
        }

        @Nested
        class WithRetryLimitPolicy {

            @Test
            void policyEvictsAfterMaxRetries() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                int maxRetries = 3;
                AtomicInteger retryCount = new AtomicInteger(0);

                EnqueuePolicy<EventMessage> retryLimitPolicy = (letter, cause) -> {
                    int currentRetry = retryCount.incrementAndGet();
                    if (currentRetry >= maxRetries) {
                        return Decisions.evict();
                    }
                    return Decisions.requeue(cause);
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, retryLimitPolicy, unitOfWorkFactory, true
                );

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();

                RuntimeException testException = new RuntimeException("processing failed");
                delegate.failingWith(testException);

                // when - process three times
                boolean result1 = testSubject.processAny().join();
                boolean result2 = testSubject.processAny().join();
                boolean result3 = testSubject.processAny().join();

                // then - first two should requeue (return false), third should evict (return true)
                assertFalse(result1);
                assertFalse(result2);
                assertTrue(result3);
                assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isZero();
            }

            @Test
            void policyRequeuesBeforeMaxRetriesAndLetterRemainsInQueue() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                int maxRetries = 5;
                AtomicInteger retryCount = new AtomicInteger(0);

                EnqueuePolicy<EventMessage> retryLimitPolicy = (letter, cause) -> {
                    int currentRetry = retryCount.incrementAndGet();
                    if (currentRetry >= maxRetries) {
                        return Decisions.evict();
                    }
                    return Decisions.requeue(cause);
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, retryLimitPolicy, unitOfWorkFactory, true
                );

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();

                RuntimeException testException = new RuntimeException("processing failed");
                delegate.failingWith(testException);

                // when - process only twice (before max retries)
                boolean result1 = testSubject.processAny().join();
                boolean result2 = testSubject.processAny().join();

                // then - both should requeue, letter still in queue with same payload
                assertFalse(result1);
                assertFalse(result2);
                assertThat(retryCount.get()).isEqualTo(2);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("test-payload");
            }
        }

        @Nested
        class WithConditionalPolicies {

            @Test
            void policyBasedOnCauseMessageEnqueuesWithCause() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                EnqueuePolicy<EventMessage> causeMessagePolicy = (letter, cause) -> {
                    if (cause.getMessage() != null && cause.getMessage().contains("transient")) {
                        return Decisions.enqueue(cause);
                    }
                    return Decisions.doNotEnqueue();
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, causeMessagePolicy, unitOfWorkFactory, true
                );

                RuntimeException transientException = new RuntimeException("transient error");
                delegate.failingWith(transientException);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then
                assertSuccessfulStream(result);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("test-payload");
                assertThat(deadLetter.cause()).hasValueSatisfying(
                        cause -> assertThat(cause.message()).contains("transient error")
                );
            }

            @Test
            void policyBasedOnCauseMessageDoesNotEnqueue() {
                // given
                EnqueuePolicy<EventMessage> causeMessagePolicy = (letter, cause) -> {
                    if (cause.getMessage() != null && cause.getMessage().contains("transient")) {
                        return Decisions.enqueue(cause);
                    }
                    return Decisions.doNotEnqueue();
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, causeMessagePolicy, unitOfWorkFactory, true
                );

                RuntimeException permanentException = new RuntimeException("permanent error");
                delegate.failingWith(permanentException);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then
                assertSuccessfulStream(result);
                assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isZero();
            }

            @Test
            void policyWithNullCauseHandlesGracefullyAndEnqueuesWithCause() {
                // given
                GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

                EnqueuePolicy<EventMessage> nullSafePolicy = (letter, cause) -> {
                    if (cause == null) {
                        return Decisions.doNotEnqueue();
                    }
                    return Decisions.enqueue(cause);
                };

                testSubject = new DeadLetteringEventHandlingComponent(
                        delegate, queue, nullSafePolicy, unitOfWorkFactory, true
                );

                RuntimeException testException = new RuntimeException("test failure");
                delegate.failingWith(testException);

                EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
                ProcessingContext context = new StubProcessingContext();

                // when
                MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);

                // then
                assertSuccessfulStream(result);
                DeadLetter<? extends EventMessage> deadLetter = getFirstDeadLetter(TEST_SEQUENCE_ID);
                assertThat(deadLetter.message().payload()).isEqualTo("test-payload");
                assertThat(deadLetter.cause()).hasValueSatisfying(
                        cause -> assertThat(cause.message()).contains("test failure")
                );
            }
        }

        private DeadLetter<? extends EventMessage> getFirstDeadLetter(String sequenceId) {
            Iterable<DeadLetter<? extends EventMessage>> sequence = queue.deadLetterSequence(sequenceId).join();
            return sequence.iterator().next();
        }
    }

    private static void assertSuccessfulStream(MessageStream.Empty<Message> result) {
        assertTrue(result.error().isEmpty());
    }

    private static void assertFailedStreamWithError(MessageStream.Empty<Message> result,
                                                    Class<? extends Throwable> expectedType,
                                                    String expectedMessage) {
        assertTrue(result.error().isPresent());
        Throwable error = result.error().get();
        assertThat(error).isInstanceOf(expectedType);
        assertThat(error.getMessage()).contains(expectedMessage);
    }

    /**
     * A stub {@link EventHandlingComponent} that tracks handled events and can be configured to fail.
     */
    private static class StubEventHandlingComponent implements EventHandlingComponent {

        private final String sequenceId;
        private final AtomicReference<EventMessage> handledEvent = new AtomicReference<>();
        private RuntimeException failWith;

        StubEventHandlingComponent(String sequenceId) {
            this.sequenceId = sequenceId;
        }

        void failingWith(RuntimeException exception) {
            this.failWith = exception;
        }

        boolean wasHandled() {
            return handledEvent.get() != null;
        }

        EventMessage handledEvent() {
            return handledEvent.get();
        }

        @Nonnull
        @Override
        public MessageStream.Empty<Message> handle(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            handledEvent.set(event);
            if (failWith != null) {
                return MessageStream.failed(failWith).ignoreEntries();
            }
            return MessageStream.empty();
        }

        @Nonnull
        @Override
        public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            return sequenceId;
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
            return Collections.emptySet();
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            // not needed
        }
    }
}

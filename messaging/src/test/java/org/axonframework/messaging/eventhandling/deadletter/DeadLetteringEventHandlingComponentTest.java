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

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.InMemorySequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.eventhandling.EventHandler;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.replay.GenericResetContext;
import org.axonframework.messaging.eventhandling.replay.ResetContext;
import org.axonframework.messaging.eventhandling.replay.ResetHandler;
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

    private DeadLetteringEventHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.systemDefaultZone();
        queue = InMemorySequencedDeadLetterQueue.<EventMessage>builder().build();
        delegate = new StubEventHandlingComponent(TEST_SEQUENCE_ID);
        enqueuePolicy = (letter, cause) -> Decisions.enqueue(cause);

        testSubject = new DeadLetteringEventHandlingComponent(delegate, queue, enqueuePolicy, true);
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
                    delegate, queue, (letter, cause) -> Decisions.ignore(), true
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
                    delegate, queue, (letter, cause) -> Decisions.doNotEnqueue(), true
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
            when(failingQueue.contains(any())).thenReturn(CompletableFuture.completedFuture(false));
            when(failingQueue.enqueue(any(), any())).thenReturn(CompletableFuture.failedFuture(enqueueException));

            testSubject = new DeadLetteringEventHandlingComponent(delegate, failingQueue, enqueuePolicy, true);

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
            when(failingQueue.contains(any())).thenReturn(CompletableFuture.completedFuture(true));
            when(failingQueue.enqueueIfPresent(any(),
                                               any())).thenReturn(CompletableFuture.failedFuture(enqueueException));

            testSubject = new DeadLetteringEventHandlingComponent(delegate, failingQueue, enqueuePolicy, true);

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
            testSubject = new DeadLetteringEventHandlingComponent(delegate, queue, enqueuePolicy, true);

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
            testSubject = new DeadLetteringEventHandlingComponent(delegate, queue, enqueuePolicy, false);

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
            // given
            ProcessingContext context = new StubProcessingContext();

            // when
            boolean result = testSubject.processAny(context).join();

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

            ProcessingContext context = new StubProcessingContext();

            // when
            boolean result = testSubject.processAny(context).join();

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
            testSubject = new DeadLetteringEventHandlingComponent(delegate, queue, enqueuePolicy, true);

            queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent1)).join();
            queue.enqueue(otherSequenceId, new GenericDeadLetter<>(otherSequenceId, testEvent2)).join();

            ProcessingContext context = new StubProcessingContext();

            // when - process only letters matching TEST_SEQUENCE_ID
            boolean result = testSubject.process(
                    letter -> letter.message().payload().toString().contains("payload-1"),
                    context
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

            ProcessingContext context = new StubProcessingContext();

            // when
            boolean result = testSubject.processAny(context).join();

            // then - letter should be requeued (still in queue)
            assertFalse(result);
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());
            assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isEqualTo(1);
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

        @Nonnull
        @Override
        public StubEventHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                    @Nonnull EventHandler handler) {
            // No-op for test purposes
            return this;
        }

        @Nonnull
        @Override
        public StubEventHandlingComponent subscribe(@Nonnull ResetHandler resetHandler) {
            // No-op for test purposes
            return this;
        }
    }
}

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

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
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
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.replay.ResetHandler;
import org.axonframework.messaging.eventhandling.sequencing.SequencingPolicy;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class validating the {@link DeadLetteringEventHandlingComponent}.
 *
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DeadLetteringEventHandlingComponentTest {

    private static final String TEST_SEQUENCE_ID = "test-aggregate-id";

    private SequencedDeadLetterQueue<EventMessage> queue;
    private RecordingEventHandlingComponent delegate;
    private EnqueuePolicy<EventMessage> enqueuePolicy;

    private DeadLetteringEventHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.systemDefaultZone();
        queue = InMemorySequencedDeadLetterQueue.<EventMessage>builder()
                                                 .maxSequences(128)
                                                 .maxSequenceSize(128)
                                                 .build();
        delegate = new RecordingEventHandlingComponent(TEST_SEQUENCE_ID);
        enqueuePolicy = (letter, cause) -> Decisions.enqueue(cause);

        testSubject = DeadLetteringEventHandlingComponent.builder()
                                                          .delegate(delegate)
                                                          .queue(queue)
                                                          .enqueuePolicy(enqueuePolicy)
                                                          .build();
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
            result.asCompletableFuture().join();

            // then
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
            result.asCompletableFuture().join();

            // then - delegate should NOT be called because sequence is already dead-lettered
            assertFalse(delegate.wasHandled());
            assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isEqualTo(2);
        }

        @Test
        void enqueuesWhenDelegateThrowsAndPolicyDecidesToEnqueue() {
            // given
            GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

            RuntimeException testException = new RuntimeException("test failure");
            delegate.setFailWith(testException);

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);
            // The stream should complete even with error (error is enqueued)
            result.asCompletableFuture().exceptionally(ex -> null).join();

            // then
            assertTrue(delegate.wasHandled());
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());
            assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isEqualTo(1);
        }

        @Test
        void enqueuesWhenPolicyDecidesToIgnore() {
            // given - Ignore means "use default behavior" which is to enqueue
            testSubject = DeadLetteringEventHandlingComponent.builder()
                                                              .delegate(delegate)
                                                              .queue(queue)
                                                              .enqueuePolicy((letter, cause) -> Decisions.ignore())
                                                              .build();

            RuntimeException testException = new RuntimeException("test failure");
            delegate.setFailWith(testException);

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);
            result.asCompletableFuture().exceptionally(ex -> null).join();

            // then - Ignore.shouldEnqueue() returns true, so event should be enqueued
            assertTrue(delegate.wasHandled());
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());
        }

        @Test
        void doesNotEnqueueWhenPolicyDecidesToDoNotEnqueue() {
            // given
            testSubject = DeadLetteringEventHandlingComponent.builder()
                                                              .delegate(delegate)
                                                              .queue(queue)
                                                              .enqueuePolicy((letter, cause) -> Decisions.doNotEnqueue())
                                                              .build();

            RuntimeException testException = new RuntimeException("test failure");
            delegate.setFailWith(testException);

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(testEvent, context);
            result.asCompletableFuture().exceptionally(ex -> null).join();

            // then
            assertTrue(delegate.wasHandled());
            assertFalse(queue.contains(TEST_SEQUENCE_ID).join());
        }
    }

    @Nested
    class WhenResetting {

        @Test
        void clearsQueueWhenAllowResetIsTrue() {
            // given
            testSubject = DeadLetteringEventHandlingComponent.builder()
                                                              .delegate(delegate)
                                                              .queue(queue)
                                                              .enqueuePolicy(enqueuePolicy)
                                                              .allowReset(true)
                                                              .build();

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());

            ResetContext resetContext = new GenericResetContext(new MessageType(String.class), "reset-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(resetContext, context);
            result.asCompletableFuture().join();

            // then
            assertFalse(queue.contains(TEST_SEQUENCE_ID).join());
            assertThat(queue.size().join()).isEqualTo(0L);
        }

        @Test
        void doesNotClearQueueWhenAllowResetIsFalse() {
            // given
            testSubject = DeadLetteringEventHandlingComponent.builder()
                                                              .delegate(delegate)
                                                              .queue(queue)
                                                              .enqueuePolicy(enqueuePolicy)
                                                              .allowReset(false)
                                                              .build();

            EventMessage testEvent = EventTestUtils.asEventMessage("test-payload");
            queue.enqueue(TEST_SEQUENCE_ID, new GenericDeadLetter<>(TEST_SEQUENCE_ID, testEvent)).join();
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());

            ResetContext resetContext = new GenericResetContext(new MessageType(String.class), "reset-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            MessageStream.Empty<Message> result = testSubject.handle(resetContext, context);
            result.asCompletableFuture().join();

            // then - queue should NOT be cleared
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
            delegate = new RecordingEventHandlingComponent(TEST_SEQUENCE_ID) {
                @Nonnull
                @Override
                public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
                    if (event.payload().toString().contains("payload-1")) {
                        return TEST_SEQUENCE_ID;
                    }
                    return otherSequenceId;
                }
            };
            testSubject = DeadLetteringEventHandlingComponent.builder()
                                                              .delegate(delegate)
                                                              .queue(queue)
                                                              .enqueuePolicy(enqueuePolicy)
                                                              .build();

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
            delegate.setFailWith(testException);

            ProcessingContext context = new StubProcessingContext();

            // when
            boolean result = testSubject.processAny(context).join();

            // then - letter should be requeued (still in queue)
            assertFalse(result);
            assertTrue(queue.contains(TEST_SEQUENCE_ID).join());
            assertThat(queue.sequenceSize(TEST_SEQUENCE_ID).join()).isEqualTo(1);
        }
    }

    @Nested
    class WhenBuilding {

        @Test
        void buildWithDefaultsSucceeds() {
            // given
            DeadLetteringEventHandlingComponent.Builder builder = DeadLetteringEventHandlingComponent.builder()
                    .delegate(delegate)
                    .queue(queue);

            // when / then
            assertDoesNotThrow(builder::build);
        }

        @Test
        void buildWithNullDelegateThrowsAxonConfigurationException() {
            // given
            DeadLetteringEventHandlingComponent.Builder builder = DeadLetteringEventHandlingComponent.builder();

            // when / then
            assertThatThrownBy(() -> builder.delegate(null))
                    .isInstanceOf(AxonConfigurationException.class);
        }

        @Test
        void buildWithoutDelegateThrowsAxonConfigurationException() {
            // given
            DeadLetteringEventHandlingComponent.Builder builder = DeadLetteringEventHandlingComponent.builder()
                    .queue(queue);

            // when / then
            assertThatThrownBy(builder::build)
                    .isInstanceOf(AxonConfigurationException.class);
        }

        @Test
        void buildWithNullQueueThrowsAxonConfigurationException() {
            // given
            DeadLetteringEventHandlingComponent.Builder builder = DeadLetteringEventHandlingComponent.builder();

            // when / then
            assertThatThrownBy(() -> builder.queue(null))
                    .isInstanceOf(AxonConfigurationException.class);
        }

        @Test
        void buildWithoutQueueThrowsAxonConfigurationException() {
            // given
            DeadLetteringEventHandlingComponent.Builder builder = DeadLetteringEventHandlingComponent.builder()
                    .delegate(delegate);

            // when / then
            assertThatThrownBy(builder::build)
                    .isInstanceOf(AxonConfigurationException.class);
        }

        @Test
        void buildWithNullEnqueuePolicyThrowsAxonConfigurationException() {
            // given
            DeadLetteringEventHandlingComponent.Builder builder = DeadLetteringEventHandlingComponent.builder();

            // when / then
            assertThatThrownBy(() -> builder.enqueuePolicy(null))
                    .isInstanceOf(AxonConfigurationException.class);
        }
    }

    @Nested
    class WhenGettingQueue {

        @Test
        void getQueueReturnsConfiguredQueue() {
            // when / then
            assertThat(testSubject.getQueue()).isSameAs(queue);
        }
    }

    /**
     * A recording {@link EventHandlingComponent} that tracks handled events and can be configured to fail.
     */
    private static class RecordingEventHandlingComponent implements EventHandlingComponent {

        private final String sequenceId;
        private final AtomicBoolean handled = new AtomicBoolean(false);
        private final AtomicReference<EventMessage> handledEvent = new AtomicReference<>();
        private RuntimeException failWith;

        RecordingEventHandlingComponent(String sequenceId) {
            this.sequenceId = sequenceId;
        }

        void setFailWith(RuntimeException exception) {
            this.failWith = exception;
        }

        boolean wasHandled() {
            return handled.get();
        }

        EventMessage handledEvent() {
            return handledEvent.get();
        }

        @Nonnull
        @Override
        public MessageStream.Empty<Message> handle(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            handled.set(true);
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
        public RecordingEventHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                         @Nonnull EventHandler handler) {
            // No-op for test purposes
            return this;
        }

        @Nonnull
        @Override
        public RecordingEventHandlingComponent subscribe(@Nonnull ResetHandler resetHandler) {
            // No-op for test purposes
            return this;
        }
    }
}

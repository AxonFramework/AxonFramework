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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SequenceNumber;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.annotation.SourceId;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationBasedEventStateApplier}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AnnotationBasedEventStateApplierTest {

    private static final EventStateApplier<TestState> eventStateApplier = new AnnotationBasedEventStateApplier<>(
            TestState.class);
    private final ProcessingContext processingContext = ProcessingContext.NONE;

    @Nested
    class BasicEventHandling {

        @Test
        void mutatesStateOnOriginalInstanceIfEventHandlerDoNotReturnsTheModelType() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void returnsStateAfterHandlingEvent() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void handlesSequenceOfEvents() {
            // given
            var state = new TestState();

            // when
            state = eventStateApplier.apply(state, domainEvent(0), processingContext);
            state = eventStateApplier.apply(state, domainEvent(1), processingContext);
            state = eventStateApplier.apply(state, domainEvent(2), processingContext);

            // then
            assertEquals("null-0-1-2", state.handledPayloads);
            assertEquals(3, state.handledCount);
        }
    }

    @Nested
    class ParameterResolution {

        @Test
        void resolvesMetadata() {
            // given
            var state = new TestState();
            var event = domainEvent(0, "sampleValue");

            // when
            state = eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null-sampleValue", state.handledMetadata);
        }

        @Test
        void resolvesSequenceNumber() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null-0", state.handledSequences);
        }

        @Test
        void resolvesSources() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null-id", state.handledSources);
        }

        @Test
        void resolvesTimestamps() {
            var timestamp = Instant.now();
            GenericEventMessage.clock = Clock.fixed(timestamp, ZoneId.systemDefault());

            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null-" + timestamp, state.handledTimestamps);
        }
    }

    @Nested
    class HandlerInvocationRules {

        @Test
        void doNotHandleNotDeclaredEventType() {
            // given
            var eventStateApplier = new AnnotationBasedEventStateApplier<>(HandlingJustStringState.class);
            var state = new HandlingJustStringState();
            var event = domainEvent(0);

            // when
            state = eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals(0, state.handledCount);
        }

        @Test
        void invokesOnlyMostSpecificHandler() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null-0", state.handledPayloads);
            assertFalse(state.objectHandlerInvoked);
            assertEquals(1, state.handledCount);
        }
    }

    // feel free to drop the support in case of some changed in EventHandling logic (like Empty stream always returned from event handling component)
    @Nested
    class RecordSupport {

        private record RecordState(String handledPayloads) {

            private static RecordState empty() {
                return new RecordState("null");
            }

            @EventSourcingHandler
            RecordState evolve(
                    Integer payload
            ) {
                return new RecordState(handledPayloads + "-" + payload);
            }
        }

        private static final EventStateApplier<RecordState> eventStateApplier = new AnnotationBasedEventStateApplier<>(
                RecordState.class);

        @Test
        void doNotMutateStateIfRecord() {
            // given
            var state = RecordState.empty();
            var event = domainEvent(0);

            // when
            eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null", state.handledPayloads);
        }

        @Test
        void returnsNewObjectIfRecord() {
            // given
            var state = RecordState.empty();
            var event = domainEvent(0);

            // when
            state = eventStateApplier.apply(state, event, processingContext);

            // then
            assertEquals("null-0", state.handledPayloads);
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void throwsStateEvolvingExceptionOnExceptionInsideEventHandler() {
            // given
            var eventStateApplier = new AnnotationBasedEventStateApplier<>(ErrorThrowingState.class);
            var state = new ErrorThrowingState();
            var event = domainEvent(0);

            // when-then
            var exception = assertThrows(StateEvolvingException.class,
                                         () -> eventStateApplier.apply(state, event, processingContext));
            assertEquals(exception.getMessage(),
                         "Failed to apply event [event#0.0.1] in order to evolve [class org.axonframework.eventsourcing.AnnotationBasedEventStateApplierTest$ErrorThrowingState] state");
            assertInstanceOf(RuntimeException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("Simulated error for event: 0"));
        }

        @Test
        void rejectsNullModel() {
            // given
            var event = domainEvent(0);

            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventStateApplier.apply(null, event, processingContext),
                         "Model may not be null");
        }

        @Test
        void rejectsNullEvent() {
            // given
            var state = new TestState();

            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventStateApplier.apply(state, null, processingContext),
                         "Event Message may not be null");
        }
    }

    private static DomainEventMessage<?> domainEvent(int seq) {
        return domainEvent(seq, null);
    }

    private static DomainEventMessage<?> domainEvent(int seq, String sampleMetaData) {
        return new GenericDomainEventMessage<>(
                "test",
                "id",
                seq,
                new MessageType("event"),
                seq, sampleMetaData == null ? MetaData.emptyInstance() : MetaData.with("sampleKey", sampleMetaData)
        );
    }

    private static class TestState {

        private String handledPayloads = "null";
        private String handledMetadata = "null";
        private String handledSequences = "null";
        private String handledSources = "null";
        private String handledTimestamps = "null";
        private int handledCount = 0;
        private boolean objectHandlerInvoked = false;

        @EventSourcingHandler
        void handle(
                Object payload
        ) {
            this.objectHandlerInvoked = true;
            this.handledCount++;
        }

        @EventSourcingHandler
        void handle(
                Integer payload,
                @MetaDataValue("sampleKey") String metadata,
                @SequenceNumber Long sequenceNumber,
                @SourceId String source,
                @Timestamp Instant timestamp
        ) {
            this.handledPayloads = handledPayloads + "-" + payload;
            this.handledMetadata = handledMetadata + "-" + metadata;
            this.handledSequences = handledSequences + "-" + sequenceNumber;
            this.handledSources = handledSources + "-" + source;
            this.handledTimestamps = handledTimestamps + "-" + timestamp;
            this.handledCount++;
        }
    }

    private static class ErrorThrowingState {

        @EventSourcingHandler
        public void handle(Integer event) {
            throw new RuntimeException("Simulated error for event: " + event);
        }
    }

    private static class HandlingJustStringState {

        private int handledCount = 0;

        @EventSourcingHandler
        void handle(String event) {
            this.handledCount++;
        }
    }
}
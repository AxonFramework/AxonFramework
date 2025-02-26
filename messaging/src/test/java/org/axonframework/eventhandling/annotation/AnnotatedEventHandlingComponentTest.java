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

package org.axonframework.eventhandling.annotation;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SequenceNumber;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.annotation.SourceId;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotatedEventHandlingComponent}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AnnotatedEventHandlingComponentTest {

    private TestState state;
    private EventHandlingComponent eventHandlingComponent;
    private final ProcessingContext processingContext = ProcessingContext.NONE;

    @BeforeEach
    void beforeEach() {
        state = new TestState();
        eventHandlingComponent = new AnnotatedEventHandlingComponent<>(state);
    }

    @Test
    void supportedEvents() {
        Set<QualifiedName> actual = eventHandlingComponent.supportedEvents();
        Set<QualifiedName> expected = new HashSet<>(Arrays.asList(
                new QualifiedName(Integer.class),
                new QualifiedName(Object.class)
        ));
        assertEquals(expected, actual);
    }

    @Nested
    class BasicEventHandling {

        @Test
        void mutatesStateOnOriginalInstanceIfEventHandlerDoNotReturnsTheModelType() {
            // given
            var event = domainEvent(0);

            // when
            eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void returnsStateAfterHandlingEvent() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void handlesSequenceOfEvents() {
            // when
            var result1 = eventHandlingComponent.handle(domainEvent(0), processingContext);
            var result2 = eventHandlingComponent.handle(domainEvent(1), processingContext);
            var result3 = eventHandlingComponent.handle(domainEvent(2), processingContext);

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
            var event = domainEvent(0, "sampleValue");

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals("null-sampleValue", state.handledMetadata);
        }

        @Test
        void resolvesSequenceNumber() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals("null-0", state.handledSequences);
        }

        @Test
        void resolvesSources() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals("null-id", state.handledSources);
        }

        @Test
        void resolvesTimestamps() {
            var timestamp = Instant.now();
            GenericEventMessage.clock = Clock.fixed(timestamp, ZoneId.systemDefault());

            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals("null-" + timestamp, state.handledTimestamps);
        }
    }

    @Nested
    class HandlerInvocationRules {

        @Test
        void doNotHandleNotDeclaredEventType() {
            // given
            var state = new HandlingJustStringState();
            var eventHandlingComponent = new AnnotatedEventHandlingComponent<>(state);
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals(0, state.handledCount);
        }

        @Test
        void invokesOnlyMostSpecificHandler() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

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

            @EventHandler
            RecordState evolve(
                    Integer payload
            ) {
                return new RecordState(handledPayloads + "-" + payload);
            }
        }

        private static final RecordState state = RecordState.empty();
        private static final EventHandlingComponent eventHandlingComponent = new AnnotatedEventHandlingComponent<>(state);

        @Test
        void doNotMutateStateIfRecord() {
            // given
            var event = domainEvent(0);

            // when
            eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals("null", state.handledPayloads);
        }

        @Test
        void returnsNewObjectIfRecord() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertEquals("null-0", state.handledPayloads);
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void throwsStateEvolvingExceptionOnExceptionInsideEventHandler() {
            // given
            var state = new ErrorThrowingState();
            var eventHandlingComponent = new AnnotatedEventHandlingComponent<>(state);
            var event = domainEvent(0);

            // when-then
            var exception = assertThrows(RuntimeException.class,
                                         () -> eventHandlingComponent.handle(event, processingContext));
            assertEquals("java.lang.RuntimeException: Simulated error for event: 0", exception.getMessage());
            assertInstanceOf(RuntimeException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("Simulated error for event: 0"));
        }

        @Test
        void rejectsNullEvent() {
            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventHandlingComponent.handle(null, processingContext),
                         "Event Message may not be null");
        }

        @Test
        void rejectsNullProcessingContext() {
            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventHandlingComponent.handle(domainEvent(0), null),
                         "Processing Context may not be null");
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

        @EventHandler
        void handle(
                Object payload
        ) {
            this.objectHandlerInvoked = true;
            this.handledCount++;
        }

        @EventHandler
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

        @EventHandler
        public void handle(Integer event) {
            throw new RuntimeException("Simulated error for event: " + event);
        }
    }

    private static class HandlingJustStringState {

        private int handledCount = 0;

        @EventHandler
        void handle(String event) {
            this.handledCount++;
        }
    }
}
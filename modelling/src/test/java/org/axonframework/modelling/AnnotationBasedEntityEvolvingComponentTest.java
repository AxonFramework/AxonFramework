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

package org.axonframework.modelling;

import org.axonframework.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.annotations.SequenceNumber;
import org.axonframework.eventhandling.annotations.Timestamp;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.annotation.MetadataValue;
import org.axonframework.messaging.annotation.SourceId;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.json.JacksonConverter;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationBasedEntityEvolvingComponent}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AnnotationBasedEntityEvolvingComponentTest {

    private static final EventConverter converter = new DelegatingEventConverter(new JacksonConverter());
    private static final ClassBasedMessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();
    private static final EntityEvolver<TestState> ENTITY_EVOLVER = new AnnotationBasedEntityEvolvingComponent<>(
            TestState.class,
            converter,
            messageTypeResolver
    );

    @Nested
    class BasicEventHandling {

        @Test
        void mutatesStateOnOriginalInstanceIfEventHandlerDoNotReturnsTheModelType() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void returnsStateAfterHandlingEvent() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void handlesSequenceOfEvents() {
            // given
            var state = new TestState();
            DomainEventMessage event0 = domainEvent(0);
            DomainEventMessage event1 = domainEvent(1);
            DomainEventMessage event2 = domainEvent(2);

            // when
            state = ENTITY_EVOLVER.evolve(state, event0, StubProcessingContext.forMessage(event0));
            state = ENTITY_EVOLVER.evolve(state, event1, StubProcessingContext.forMessage(event1));
            state = ENTITY_EVOLVER.evolve(state, event2, StubProcessingContext.forMessage(event2));

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
            state = ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null-sampleValue", state.handledMetadata);
        }

        @Test
        void resolvesSequenceNumber() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null-0", state.handledSequences);
        }

        @Test
        void resolvesSources() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

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
            state = ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null-" + timestamp, state.handledTimestamps);
        }

        @AfterEach
        void afterEach() {
            GenericEventMessage.clock = Clock.systemUTC();
        }
    }

    @Nested
    class HandlerInvocationRules {

        @Test
        void doNotHandleNotDeclaredEventType() {
            // given
            var eventSourcedComponent = new AnnotationBasedEntityEvolvingComponent<>(HandlingJustStringState.class,
                                                                                     converter,
                                                                                     messageTypeResolver);
            var state = new HandlingJustStringState();
            var event = domainEvent(0);

            // when
            state = eventSourcedComponent.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals(0, state.handledCount);
        }

        @Test
        void invokesOnlyMostSpecificHandler() {
            // given
            var state = new TestState();
            var event = domainEvent(0);

            // when
            state = ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null-0", state.handledPayloads);
            assertFalse(state.objectHandlerInvoked);
            assertEquals(1, state.handledCount);
        }
    }

    @Nested
    class RecordSupport {

        @SuppressWarnings("unused")
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

        private static final EntityEvolver<RecordState> ENTITY_EVOLVER = new AnnotationBasedEntityEvolvingComponent<>(
                RecordState.class,
                converter, messageTypeResolver
        );

        @Test
        void doNotMutateGivenStateIfRecord() {
            // given
            var state = RecordState.empty();
            var event = domainEvent(0);

            // when
            ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null", state.handledPayloads);
        }

        @Test
        void returnsNewObjectIfRecord() {
            // given
            var state = RecordState.empty();
            var event = domainEvent(0);

            // when
            state = ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null-0", state.handledPayloads);
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void throwsStateEvolvingExceptionOnExceptionInsideEventHandler() {
            // given
            var testSubject = new AnnotationBasedEntityEvolvingComponent<>(ErrorThrowingState.class,
                                                                           converter,
                                                                           messageTypeResolver);
            var state = new ErrorThrowingState();
            var event = domainEvent(0);

            // when-then
            var exception = assertThrows(StateEvolvingException.class,
                                         () -> testSubject.evolve(state, event, StubProcessingContext.forMessage(event)));
            assertEquals(
                    "Failed to apply event [java.lang.Integer#0.0.1] in order to evolve [class org.axonframework.modelling.AnnotationBasedEntityEvolvingComponentTest$ErrorThrowingState] state",
                    exception.getMessage()
            );
            assertInstanceOf(RuntimeException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("Simulated error for event: 0"));
        }

        @Test
        void rejectsNullModel() {
            // given
            var event = domainEvent(0);

            // when-then
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> ENTITY_EVOLVER.evolve(null, event, StubProcessingContext.forMessage(event)),
                         "Model may not be null");
        }
    }

    private static DomainEventMessage domainEvent(int seq) {
        return domainEvent(seq, null);
    }

    private static DomainEventMessage domainEvent(int seq, String sampleMetadata) {
        return new GenericDomainEventMessage(
                "test",
                "id",
                seq,
                new MessageType(Integer.class),
                seq, sampleMetadata == null ? Metadata.emptyInstance() : Metadata.with("sampleKey", sampleMetadata)
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
                @MetadataValue("sampleKey") String metadata,
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

    @SuppressWarnings("unused")
    private static class ErrorThrowingState {

        @EventHandler
        public void handle(Integer event) {
            throw new RuntimeException("Simulated error for event: " + event);
        }
    }

    @SuppressWarnings("unused")
    private static class HandlingJustStringState {

        private int handledCount = 0;

        @EventHandler
        void handle(String event) {
            this.handledCount++;
        }
    }
}
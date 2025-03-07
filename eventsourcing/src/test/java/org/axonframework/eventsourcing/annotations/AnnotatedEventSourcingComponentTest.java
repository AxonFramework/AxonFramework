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

package org.axonframework.eventsourcing.annotations;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SequenceNumber;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.eventhandling.annotation.AnnotatedEventHandlingComponent;
import org.axonframework.eventsourcing.EventSourcingComponent;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
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
import static org.mockito.Mockito.*;


/**
 * Test class validating the {@link AnnotatedEventSourcingComponent}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AnnotatedEventSourcingComponentTest {

    private TestEventHandler eventHandler;
    private EventSourcingComponent eventSourcingComponent;
    private final ProcessingContext processingContext = ProcessingContext.NONE;

    @BeforeEach
    void beforeEach() {
        eventHandler = new TestEventHandler();
        eventSourcingComponent = new AnnotatedEventSourcingComponent<>(eventHandler);
    }

    @Test
    void supportedEvents() {
        Set<QualifiedName> actual = eventSourcingComponent.supportedEvents();
        Set<QualifiedName> expected = new HashSet<>(Arrays.asList(
                new QualifiedName(Integer.class),
                new QualifiedName(Object.class)
        ));
        assertEquals(expected, actual);
    }

    @Nested
    class BasicEventHandling {

        @Test
        void sourceReturnsNullIfHandlerReturnTypeIsVoid() {
            // given
            var event = domainEvent(0);

            // when
            MessageStream.Single<Message<TestEventHandler>> result = eventSourcingComponent.source(event,
                                                                                                   processingContext)
                                                                                           .cast();
            TestEventHandler sourcedState = sourcedState(result);

            // then
            assertSuccessfulStream(result);
            assertNull(sourcedState);
        }

        @Test
        void canMutateStateInEventHandler() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext).cast();

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", eventHandler.handledPayloads);
        }

        @Test
        void returnsSingleStreamAfterHandlingEvent() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertInstanceOf(MessageStream.Single.class, result);
            assertEquals("null-0", eventHandler.handledPayloads);
        }

        @Test
        void handlesSequenceOfEvents() {
            // when
            var result1 = eventSourcingComponent.source(domainEvent(0), processingContext);
            var result2 = eventSourcingComponent.source(domainEvent(1), processingContext);
            var result3 = eventSourcingComponent.source(domainEvent(2), processingContext);

            // then
            assertSuccessfulStream(result1);
            assertSuccessfulStream(result2);
            assertSuccessfulStream(result3);
            assertEquals("null-0-1-2", eventHandler.handledPayloads);
            assertEquals(3, eventHandler.handledCount);
        }

        private <T> T sourcedState(MessageStream.Single<Message<T>> stream) {
            return stream.asCompletableFuture().join().message().getPayload();
        }
    }


    @Nested
    class ParameterResolution {

        @Test
        void resolvesMetadata() {
            // given
            var event = domainEvent(0, "sampleValue");

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null-sampleValue", eventHandler.handledMetadata);
        }

        @Test
        void resolvesSequenceNumber() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", eventHandler.handledSequences);
        }

        @Test
        void resolvesSources() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null-id", eventHandler.handledSources);
        }

        @Test
        void resolvesTimestamps() {
            var timestamp = Instant.now();
            GenericEventMessage.clock = Clock.fixed(timestamp, ZoneId.systemDefault());

            // given
            var event = domainEvent(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null-" + timestamp, eventHandler.handledTimestamps);
        }
    }

    @Nested
    class HandlerInvocationRules {

        @Test
        void doNotHandleNotDeclaredEventType() {
            // given
            var eventHandler = new HandlingJustStringEventHandler();
            var eventHandlingComponent = new AnnotatedEventSourcingComponent<>(eventHandler);
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals(0, eventHandler.handledCount);
        }

        @Test
        void invokesOnlyMostSpecificHandler() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", eventHandler.handledPayloads);
            assertFalse(eventHandler.objectHandlerInvoked);
            assertEquals(1, eventHandler.handledCount);
        }
    }

    @Nested
    class RecordSupport {

        private record RecordState(String handledPayloads) {

            private static RecordSupport.RecordState initial() {
                return new RecordSupport.RecordState("null");
            }

            @EventSourcingHandler
            RecordSupport.RecordState evolve(
                    Integer payload
            ) {
                return new RecordSupport.RecordState(handledPayloads + "-" + payload);
            }
        }

        private static final EventSourcingComponent eventSourcingComponent = new AnnotatedEventSourcingComponent<>(
                RecordState.initial());

        @Test
        void doNotMutateGivenStateIfRecord() {
            // given
            var state = RecordState.initial();
            var event = domainEvent(0);

            // when
            var result = eventSourcingComponent.source(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null", state.handledPayloads);
        }

        @Test
        void returnsNewObjectIfRecord() {
            // given
            var event = domainEvent(0);

            // when
            RecordState sourcedState = sourcedState(event);

            // then
            assertEquals("null-0", sourcedState.handledPayloads);
        }

        private <T> T sourcedState(DomainEventMessage<?> event) {
            MessageStream.Single<Message<T>> sourcingStream = eventSourcingComponent
                    .source(event, processingContext)
                    .cast();
            return sourcingStream.asCompletableFuture().join().message().getPayload();
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void returnsFailedMessageStreamIfExceptionThrownInsideEventHandler() {
            // given
            var eventHandler = new ErrorThrowingEventHandler();
            var eventHandlingComponent = new AnnotatedEventSourcingComponent<>(eventHandler);
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.source(event, processingContext);

            // then
            assertTrue(result.error().isPresent());
            var exception = result.error().get();
            assertInstanceOf(RuntimeException.class, exception);
            assertEquals("Simulated error for event: 0", exception.getMessage());
        }

        @Test
        void propagateExceptionIfExceptionThrownOutOfEventHandler() {
            // given
            var eventHandler = new TestEventHandler();
            AnnotatedHandlerInspector<TestEventHandler> errorThrowingDependency = mock(AnnotatedHandlerInspector.class);
            when(errorThrowingDependency.getHandlers(any())).thenThrow(new RuntimeException("Simulated error"));
            var eventHandlingComponent = new AnnotatedEventSourcingComponent<>(eventHandler, errorThrowingDependency);
            var event = domainEvent(0);

            // when-then
            var exception = assertThrows(
                    RuntimeException.class,
                    () -> eventHandlingComponent.source(event, processingContext)
            );

            // then
            assertEquals("Simulated error", exception.getMessage());
        }

        @Test
        void rejectsNullEvent() {
            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventSourcingComponent.source(null, processingContext),
                         "Event Message may not be null");
        }

        @Test
        void rejectsNullProcessingContext() {
            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventSourcingComponent.source(domainEvent(0), null),
                         "Processing Context may not be null");
        }
    }


    private static void assertSuccessfulStream(MessageStream<?> result) {
        assertTrue(result.error().isEmpty());
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

    private static class TestEventHandler {

        private String handledPayloads = "null";
        private String handledMetadata = "null";
        private String handledSequences = "null";
        private String handledSources = "null";
        private String handledTimestamps = "null";
        private int handledCount = 0;
        private boolean objectHandlerInvoked = false;

        @EventSourcingHandler
        void handle(Object payload) {
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

    private static class ErrorThrowingEventHandler {

        @EventSourcingHandler
        public void handle(Integer event) {
            throw new RuntimeException("Simulated error for event: " + event);
        }
    }

    private static class HandlingJustStringEventHandler {

        private int handledCount = 0;

        @EventSourcingHandler
        void handle(String event) {
            this.handledCount++;
        }
    }
}

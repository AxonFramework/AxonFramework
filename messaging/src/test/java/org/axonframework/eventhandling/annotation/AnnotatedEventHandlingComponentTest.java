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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
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

    private TestEventHandlingComponent state;
    private EventHandlingComponent eventHandlingComponent;
    private final ProcessingContext processingContext = ProcessingContext.NONE;

    @BeforeEach
    void beforeEach() {
        state = new TestEventHandlingComponent();
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
        void canMutateStateInEventHandler() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void returnsEmptyStreamAfterHandlingEvent() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertInstanceOf(MessageStream.Empty.class, result);
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void handlesSequenceOfEvents() {
            // when
            var result1 = eventHandlingComponent.handle(domainEvent(0), processingContext);
            var result2 = eventHandlingComponent.handle(domainEvent(1), processingContext);
            var result3 = eventHandlingComponent.handle(domainEvent(2), processingContext);

            // then
            assertSuccessfulStream(result1);
            assertSuccessfulStream(result2);
            assertSuccessfulStream(result3);
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
            assertSuccessfulStream(result);
            assertEquals("null-sampleValue", state.handledMetadata);
        }

        @Test
        void resolvesSequenceNumber() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", state.handledSequences);
        }

        @Test
        void resolvesSources() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertSuccessfulStream(result);
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
            assertSuccessfulStream(result);
            assertEquals("null-" + timestamp, state.handledTimestamps);
        }
    }

    private static void assertSuccessfulStream(MessageStream.Empty<Message<Void>> result) {
        assertTrue(result.error().isEmpty());
    }

    @Nested
    class HandlerInvocationRules {

        @Test
        void doNotHandleNotDeclaredEventType() {
            // given
            var state = new HandlingJustStringEventHandlingComponent();
            var eventHandlingComponent = new AnnotatedEventHandlingComponent<>(state);
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals(0, state.handledCount);
        }

        @Test
        void invokesOnlyMostSpecificHandler() {
            // given
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", state.handledPayloads);
            assertFalse(state.objectHandlerInvoked);
            assertEquals(1, state.handledCount);
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void returnsFailedMessageStreamIfExceptionThrownInsideEventHandler() {
            // given
            var state = new ErrorThrowingEventHandlingComponent();
            var eventHandlingComponent = new AnnotatedEventHandlingComponent<>(state);
            var event = domainEvent(0);

            // when
            var result = eventHandlingComponent.handle(event, processingContext);

            // then
            assertTrue(result.error().isPresent());
            var exception = result.error().get();
            assertInstanceOf(RuntimeException.class, exception);
            assertEquals("Simulated error for event: 0", exception.getMessage());
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

    private static class TestEventHandlingComponent {

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

    private static class ErrorThrowingEventHandlingComponent {

        @EventHandler
        public void handle(Integer event) {
            throw new RuntimeException("Simulated error for event: " + event);
        }
    }

    private static class HandlingJustStringEventHandlingComponent {

        private int handledCount = 0;

        @EventHandler
        void handle(String event) {
            this.handledCount++;
        }
    }
}
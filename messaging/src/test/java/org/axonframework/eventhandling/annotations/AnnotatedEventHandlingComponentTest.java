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

package org.axonframework.eventhandling.annotations;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.LegacyResources;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotations.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotations.MetadataValue;
import org.axonframework.messaging.annotations.SourceId;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.eventhandling.sequencing.SequentialPolicy.FULL_SEQUENTIAL_POLICY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotatedEventHandlingComponent}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AnnotatedEventHandlingComponentTest {

    private static final String AGGREGATE_TYPE = "test";
    private static final String AGGREGATE_IDENTIFIER = "id";
    private TestEventHandler eventHandler;
    private EventHandlingComponent eventHandlingComponent;

    @BeforeEach
    void beforeEach() {
        eventHandler = new TestEventHandler();
        eventHandlingComponent = annotatedEventHandlingComponent(eventHandler);
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
            var event = eventMessage(0);

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", eventHandler.handledPayloads);
        }

        @Test
        void returnsEmptyStreamAfterHandlingEvent() {
            // given
            var event = eventMessage(0);

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertSuccessfulStream(result);
            assertInstanceOf(MessageStream.Empty.class, result);
            assertEquals("null-0", eventHandler.handledPayloads);
        }

        @Test
        void handlesSequenceOfEvents() {
            // when
            EventMessage event0 = eventMessage(0);
            var result1 = eventHandlingComponent.handle(event0, messageProcessingContext(event0));
            EventMessage event1 = eventMessage(1);
            var result2 = eventHandlingComponent.handle(event1, messageProcessingContext(event1));
            EventMessage event2 = eventMessage(2);
            var result3 = eventHandlingComponent.handle(event2, messageProcessingContext(event2));

            // then
            assertSuccessfulStream(result1);
            assertSuccessfulStream(result2);
            assertSuccessfulStream(result3);
            assertEquals("null-0-1-2", eventHandler.handledPayloads);
            assertEquals(3, eventHandler.handledCount);
        }
    }

    @Nested
    class ParameterResolution {

        @Test
        void resolvesMetadata() {
            // given
            var event = eventMessage(0, "sampleValue");

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertSuccessfulStream(result);
            assertEquals("null-sampleValue", eventHandler.handledMetadata);
        }

        @Test
        void resolvesSequenceNumber() {
            // given
            var event = eventMessage(0);

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", eventHandler.handledSequences);
        }

        @Test
        void resolvesSources() {
            // given
            var event = eventMessage(0);

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertSuccessfulStream(result);
            assertEquals("null-id", eventHandler.handledSources);
        }

        @Test
        void resolvesTimestamps() {
            var timestamp = Instant.now();
            GenericEventMessage.clock = Clock.fixed(timestamp, ZoneId.systemDefault());

            // given
            var event = eventMessage(0);

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertSuccessfulStream(result);
            assertEquals("null-" + timestamp, eventHandler.handledTimestamps);
        }

        @AfterEach
        void afterEach() {
            GenericEventMessage.clock = Clock.systemUTC();
        }
    }

    private static void assertSuccessfulStream(MessageStream.Empty<Message> result) {
        assertTrue(result.error().isEmpty());
    }

    @Nested
    class HandlerInvocationRules {

        @Test
        void doNotHandleNotDeclaredEventType() {
            // given
            var eventHandler = new HandlingJustStringEventHandler();
            var eventHandlingComponent = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage(0);

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertTrue(result.error().isPresent());
            var exception = result.error().get();
            assertInstanceOf(RuntimeException.class, exception);
            assertEquals(
                    "No handler found for event with name [java.lang.Integer] in component "
                            + "[org.axonframework.eventhandling.SimpleEventHandlingComponent]",
                    exception.getMessage()
            );
            assertEquals(0, eventHandler.handledCount);
        }

        @Test
        void invokesOnlyMostSpecificHandler() {
            // given
            var event = eventMessage(0);

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertSuccessfulStream(result);
            assertEquals("null-0", eventHandler.handledPayloads);
            assertFalse(eventHandler.objectHandlerInvoked);
            assertEquals(1, eventHandler.handledCount);
        }

        @Test
        void invokesHandlerWithCustomName() {
            // given
            var eventHandler = new HandlingNamedEventHandler();
            var eventHandlingComponent = annotatedEventHandlingComponent(eventHandler);
            var event = new GenericEventMessage(
                    new MessageType("CustomEventName"),
                    123
            );

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

            // then
            assertSuccessfulStream(result);
            assertEquals(1, eventHandler.handledNamed);
            assertEquals(0, eventHandler.handledNotNamed);
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void returnsFailedMessageStreamIfExceptionThrownInsideEventHandler() {
            // given
            var eventHandler = new ErrorThrowingEventHandler();
            var eventHandlingComponent = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage(0);

            // when
            var result = eventHandlingComponent.handle(event, messageProcessingContext(event));

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
                         () -> eventHandlingComponent.handle(null, messageProcessingContext(null)),
                         "Event Message may not be null");
        }

        @Test
        void rejectsNullProcessingContext() {
            // when-then
            assertThrows(NullPointerException.class,
                         () -> eventHandlingComponent.handle(eventMessage(0), null),
                         "Processing Context may not be null");
        }
    }

    @Nested
    class SupportedEvents {

        @Test
        void testMethodWithPayload() {
            // given
            var eventHandler = new HandlingJustStringEventHandler();
            var eventHandlingComponent = annotatedEventHandlingComponent(eventHandler);

            // when
            var supportedEvents = eventHandlingComponent.supportedEvents();

            // then
            assertThat(supportedEvents).containsExactlyInAnyOrder(
                    new QualifiedName(String.class)
            );
        }

        @Test
        void testMethodsWithObjectAndPayload() {
            // given
            var eventHandler = new TestEventHandler();
            var eventHandlingComponent = annotatedEventHandlingComponent(eventHandler);

            // when
            var supportedEvents = eventHandlingComponent.supportedEvents();

            // then
            assertThat(supportedEvents).containsExactlyInAnyOrder(
                    new QualifiedName(Object.class),
                    new QualifiedName(Integer.class)
            );
        }
    }

    @Nested
    class SequenceIdentifierFor {

        @Test
        void defaultSequencingPolicyIsFullSequentialIfAggregateIdentifierNotInProcessingContext() {
            // given
            var event = eventMessage(0);

            // when
            var sequenceIdentifier = eventHandlingComponent.sequenceIdentifierFor(event, new StubProcessingContext());

            // then
            assertThat(sequenceIdentifier).isEqualTo(FULL_SEQUENTIAL_POLICY);
        }

        @Test
        void defaultSequencingPolicyIsSequentialPerAggregateIfAggregateIdentifierIsPresentInProcessingContext() {
            // given
            var event = eventMessage(0);

            // when
            var sequenceIdentifier = eventHandlingComponent.sequenceIdentifierFor(event, messageProcessingContext(event));

            // then
            assertThat(sequenceIdentifier).isEqualTo("id");
        }
    }

    private static EventMessage eventMessage(int seq) {
        return eventMessage(seq, null);
    }

    private static EventMessage eventMessage(int seq, String sampleMetadata) {
        return new GenericEventMessage(
                new MessageType(Integer.class),
                seq,
                sampleMetadata == null ? Metadata.emptyInstance() : Metadata.with("sampleKey", sampleMetadata)
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

        @EventHandler
        void handle(Object payload) {
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

    private static class ErrorThrowingEventHandler {

        @EventHandler
        public void handle(Integer event) {
            throw new RuntimeException("Simulated error for event: " + event);
        }
    }

    private static class HandlingJustStringEventHandler {

        private int handledCount = 0;

        @EventHandler
        void handle(String event) {
            this.handledCount++;
        }
    }

    private static class HandlingNamedEventHandler {

        private int handledNamed = 0;
        private int handledNotNamed = 0;

        @EventHandler(eventName = "CustomEventName")
        void handleNamed() {
            handledNamed++;
        }


        @EventHandler
        void handleNotNamed() {
            handledNotNamed++;
        }

    }

    @Nonnull
    private static AnnotatedEventHandlingComponent<?> annotatedEventHandlingComponent(Object eventHandler) {
        return new AnnotatedEventHandlingComponent<>(
                eventHandler,
                ClasspathParameterResolverFactory.forClass(eventHandler.getClass())
        );
    }

    @Nonnull
    private static ProcessingContext messageProcessingContext(EventMessage event) {
        var payload = event.payloadAs(Integer.class);
        return StubProcessingContext
                .withComponent(Converter.class, PassThroughConverter.INSTANCE)
                .withMessage(event)
                .withResource(LegacyResources.AGGREGATE_TYPE_KEY, AGGREGATE_TYPE)
                .withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, AGGREGATE_IDENTIFIER)
                .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, payload.longValue());
    }
}
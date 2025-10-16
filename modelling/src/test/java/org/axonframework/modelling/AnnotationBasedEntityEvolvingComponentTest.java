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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.eventhandling.conversion.EventConverter;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.annotations.SequenceNumber;
import org.axonframework.eventhandling.annotations.Timestamp;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.annotations.MetadataValue;
import org.axonframework.messaging.annotations.SourceId;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.modelling.annotations.AnnotationBasedEntityEvolvingComponent;
import org.axonframework.serialization.json.JacksonConverter;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.axonframework.eventhandling.EventTestUtils.asEventMessage;
import static org.axonframework.eventhandling.EventTestUtils.createEvent;
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
            var event = createEvent(0);
            var context = StubProcessingContext.forMessage(event, "id", 0, "test");

            // when
            ENTITY_EVOLVER.evolve(state, event, context);

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void returnsStateAfterHandlingEvent() {
            // given
            var state = new TestState();
            var event = createEvent(0);
            var context = StubProcessingContext.forMessage(event, "id", 0, "test");

            // when
            state = ENTITY_EVOLVER.evolve(state, event, context);

            // then
            assertEquals("null-0", state.handledPayloads);
        }

        @Test
        void handlesSequenceOfEvents() {
            // given
            var state = new TestState();
            EventMessage event0 = createEvent(0);
            var context0 = StubProcessingContext.forMessage(event0, "id", 0, "test");
            EventMessage event1 = createEvent(1);
            var context1 = StubProcessingContext.forMessage(event1, "id", 1, "test");
            EventMessage event2 = createEvent(2);
            var context2 = StubProcessingContext.forMessage(event2, "id", 2, "test");

            // when
            state = ENTITY_EVOLVER.evolve(state, event0, context0);
            state = ENTITY_EVOLVER.evolve(state, event1, context1);
            state = ENTITY_EVOLVER.evolve(state, event2, context2);

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
            var event = new GenericEventMessage(new MessageType(Integer.class),
                                                0,
                                                Metadata.with("sampleKey", "sampleValue"));
            var context = StubProcessingContext.forMessage(event, "id", 0, "test");

            // when
            state = ENTITY_EVOLVER.evolve(state, event, context);

            // then
            assertEquals("null-sampleValue", state.handledMetadata);
        }

        @Test
        void resolvesSequenceNumber() {
            // given
            var state = new TestState();
            var event = createEvent(0);
            var context = StubProcessingContext.forMessage(event, "id", 0, "test");

            // when
            state = ENTITY_EVOLVER.evolve(state, event, context);

            // then
            assertEquals("null-0", state.handledSequences);
        }

        @Test
        void resolvesSources() {
            // given
            var state = new TestState();
            var event = createEvent(0);
            var context = StubProcessingContext.forMessage(event, "id", 0, "test");

            // when
            state = ENTITY_EVOLVER.evolve(state, event, context);

            // then
            assertEquals("null-id", state.handledSources);
        }

        @Test
        void resolvesTimestamps() {
            var timestamp = Instant.now();
            GenericEventMessage.clock = Clock.fixed(timestamp, ZoneId.systemDefault());

            // given
            var state = new TestState();
            var event = createEvent(0);
            var context = StubProcessingContext.forMessage(event, "id", 0, "test");

            // when
            state = ENTITY_EVOLVER.evolve(state, event, context);

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
            var event = createEvent(0);

            // when
            state = eventSourcedComponent.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals(0, state.handledCount);
        }

        @Test
        void invokesOnlyMostSpecificHandler() {
            // given
            var state = new TestState();
            var event = createEvent(0);
            var context = StubProcessingContext.forMessage(event, "id", 0, "test");

            // when
            state = ENTITY_EVOLVER.evolve(state, event, context);

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
            var event = createEvent(0);

            // when
            ENTITY_EVOLVER.evolve(state, event, StubProcessingContext.forMessage(event));

            // then
            assertEquals("null", state.handledPayloads);
        }

        @Test
        void returnsNewObjectIfRecord() {
            // given
            var state = RecordState.empty();
            var event = createEvent(0);

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
            var event = createEvent(0);

            // when-then
            var exception = assertThrows(StateEvolvingException.class,
                                         () -> testSubject.evolve(state,
                                                                  event,
                                                                  StubProcessingContext.forMessage(event)));
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
            var event = createEvent(0);

            // when-then
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> ENTITY_EVOLVER.evolve(null, event, StubProcessingContext.forMessage(event)),
                         "Model may not be null");
        }
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

    @Nested
    class PolymorphicEntitySupport {

        /**
         * These tests demonstrate support for truly polymorphic entities where the entity type itself changes
         * between different concrete implementations of a sealed hierarchy.
         * <p>
         * The implementation uses Java's sealed types feature to automatically discover all concrete types
         * in a sealed hierarchy. When creating an {@link AnnotationBasedEntityEvolvingComponent} with a sealed
         * interface or abstract class, it recursively scans all permitted subtypes and registers their event handlers
         * with the {@link org.axonframework.messaging.annotations.AnnotatedHandlerInspector}.
         * <p>
         * This allows event handlers defined on concrete implementations (like InitialCourse, CreatedCourse,
         * PublishedCourse) to be discovered even when the component is created with the interface type (Course.class).
         */

        // Sealed interface where the ENTITY ITSELF is polymorphic (not state inside)
        sealed interface Course permits InitialCourse, CreatedCourse, PublishedCourse {
        }

        @SuppressWarnings("unused")
        private record InitialCourse() implements Course {
            @EventHandler
            CreatedCourse onCreate(String courseCreatedEvent) {
                // Handler returns a different type of entity (sibling type)
                return new CreatedCourse(courseCreatedEvent);
            }
        }

        @SuppressWarnings("unused")
        private record CreatedCourse(String courseName) implements Course {
            @EventHandler
            PublishedCourse onPublish(Integer coursePublishedEvent) {
                // Handler returns a different type of entity (sibling type)
                return new PublishedCourse(courseName, coursePublishedEvent);
            }
        }

        @SuppressWarnings("unused")
        private record PublishedCourse(String courseName, Integer publishedVersion) implements Course {
        }

        private static final EntityEvolver<Course> COURSE_EVOLVER = new AnnotationBasedEntityEvolvingComponent<>(
                Course.class,
                converter,
                messageTypeResolver
        );

        @Test
        void evolvesPolymorphicEntityFromInitialToCreatedType() {
            // given
            Course course = new InitialCourse();
            var event = asEventMessage("Introduction to Axon");
            var context = StubProcessingContext.forMessage(event);

            // when
            course = COURSE_EVOLVER.evolve(course, event, context);

            // then
            assertInstanceOf(CreatedCourse.class, course);
            assertEquals("Introduction to Axon", ((CreatedCourse) course).courseName());
        }

        @Test
        void evolvesPolymorphicEntityFromCreatedToPublishedType() {
            // given
            Course course = new CreatedCourse("Introduction to Axon");
            var event = asEventMessage(1);
            var context = StubProcessingContext.forMessage(event);

            // when
            course = COURSE_EVOLVER.evolve(course, event, context);

            // then
            assertInstanceOf(PublishedCourse.class, course);
            assertEquals("Introduction to Axon", ((PublishedCourse) course).courseName());
            assertEquals(1, ((PublishedCourse) course).publishedVersion());
        }

        @Test
        void evolvesPolymorphicEntityThroughMultipleTypeTransitions() {
            // given
            Course course = new InitialCourse();
            var createEvent = asEventMessage("Introduction to Axon");
            var publishEvent = asEventMessage(1);
            var createContext = StubProcessingContext.forMessage(createEvent);
            var publishContext = StubProcessingContext.forMessage(publishEvent);

            // when
            course = COURSE_EVOLVER.evolve(course, createEvent, createContext);
            course = COURSE_EVOLVER.evolve(course, publishEvent, publishContext);

            // then
            assertInstanceOf(PublishedCourse.class, course);
            assertEquals("Introduction to Axon", ((PublishedCourse) course).courseName());
            assertEquals(1, ((PublishedCourse) course).publishedVersion());
        }
    }
}
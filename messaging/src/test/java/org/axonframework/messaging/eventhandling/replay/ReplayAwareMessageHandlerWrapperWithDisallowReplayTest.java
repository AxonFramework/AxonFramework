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

package org.axonframework.messaging.eventhandling.replay;

import org.axonframework.conversion.PassThroughConverter;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.AnnotatedEventHandlingComponent;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.replay.annotation.AllowReplay;
import org.axonframework.messaging.eventhandling.replay.annotation.DisallowReplay;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the {@link DisallowReplay} annotation has the expected behavior.
 * <p>
 * Tests validate:
 * <ul>
 *   <li>Class-level {@code @DisallowReplay} blocks all handlers during replay</li>
 *   <li>Method-level {@code @AllowReplay} can override class-level {@code @DisallowReplay}</li>
 *   <li>Method-level {@code @DisallowReplay} blocks individual handlers during replay</li>
 *   <li>{@code supportsReset()} returns appropriate values based on replay configuration</li>
 *   <li>Handlers receive unwrapped tracking tokens (not {@link ReplayToken})</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class ReplayAwareMessageHandlerWrapperWithDisallowReplayTest {

    private TrackingToken replayToken;
    private TrackingToken regularToken;

    @BeforeEach
    void setUp() {
        regularToken = new GlobalSequenceTrackingToken(1L);
        replayToken = ReplayToken.createReplayToken(regularToken);
    }

    @Nested
    class GivenClassLevelDisallowReplayWithMethodLevelAllowReplay {

        private ClassDisallowedWithMethodAllowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new ClassDisallowedWithMethodAllowedHandler();
            testSubject = new AnnotatedEventHandlingComponent<>(
                    handler,
                    ClasspathParameterResolverFactory.forClass(ClassDisallowedWithMethodAllowedHandler.class)
            );
        }

        @Test
        void allowReplayMethodHandlesEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
        }

        @Test
        void disallowReplayMethodSkipsEventsDuringReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedLongs).isEmpty();
        }

        @Test
        void disallowReplayMethodHandlesEventsOutsideReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, regularContext(longEvent));

            // then
            assertThat(handler.receivedLongs).containsExactly(42L);
        }

        @Test
        void allowReplayMethodHandlesEventsOutsideReplay() {
            // given
            var stringEvent = stringEvent();

            // when
            testSubject.handle(stringEvent, regularContext(stringEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
        }

    }

    @Nested
    class GivenMethodLevelDisallowReplay {

        private MethodLevelDisallowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new MethodLevelDisallowedHandler();
            testSubject = new AnnotatedEventHandlingComponent<>(
                    handler,
                    ClasspathParameterResolverFactory.forClass(MethodLevelDisallowedHandler.class)
            );
        }

        @Test
        void allowReplayMethodHandlesEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
        }

        @Test
        void disallowReplayMethodSkipsEventsDuringReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedLongs).isEmpty();
        }

        @Test
        void disallowReplayMethodHandlesEventsOutsideReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, regularContext(longEvent));

            // then
            assertThat(handler.receivedLongs).containsExactly(42L);
        }
    }

    @Nested
    class GivenClassLevelDisallowReplayWithoutOverrides {

        private FullyDisallowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new FullyDisallowedHandler();
            testSubject = new AnnotatedEventHandlingComponent<>(
                    handler,
                    ClasspathParameterResolverFactory.forClass(FullyDisallowedHandler.class)
            );
        }

        @Test
        void skipsAllEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();
            var longEvent = longEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedStrings).isEmpty();
            assertThat(handler.receivedLongs).isEmpty();
        }

        @Test
        void handlesAllEventsOutsideReplay() {
            // given
            var stringEvent = stringEvent();
            var longEvent = longEvent();

            // when
            testSubject.handle(stringEvent, regularContext(stringEvent));
            testSubject.handle(longEvent, regularContext(longEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
            assertThat(handler.receivedLongs).containsExactly(42L);
        }
    }

    /**
     * Tests for {@code supportsReset()} behavior.
     * <p>
     * In AF5, {@code supportsReset()} returns {@code true} if at least one handler allows replay:
     * <ul>
     *   <li>If class has {@code @DisallowReplay} but at least one method has {@code @AllowReplay},
     *       the component IS wrapped with {@code SimpleResetEventHandlingComponent}, so
     *       {@code supportsReset()} returns {@code true}</li>
     *   <li>If class has {@code @DisallowReplay} and NO method has {@code @AllowReplay},
     *       the component IS wrapped with {@code ReplayBlockingEventHandlingComponent},
     *       which returns {@code false} for {@code supportsReset()}</li>
     *   <li>If no class-level {@code @DisallowReplay}, default behavior applies
     *       ({@code supportsReset()} returns {@code false})</li>
     * </ul>
     */
    @Nested
    class SupportsReset {

        @Test
        void classLevelDisallowWithMethodAllowReturnsTrue() {
            // given
            var handler = new ClassDisallowedWithMethodAllowedHandler();
            var testSubject = AnnotatedEventHandlingComponent.create(
                    handler,
                    ClasspathParameterResolverFactory.forClass(ClassDisallowedWithMethodAllowedHandler.class)
            );

            // when / then
            // Class has @DisallowReplay but one method has @AllowReplay
            // Wrapped with SimpleResetEventHandlingComponent which returns true
            assertThat(testSubject.supportsReset()).isTrue();
        }

        @Test
        void methodLevelOnlyReturnsFalse() {
            // given
            var handler = new MethodLevelDisallowedHandler();
            var testSubject = AnnotatedEventHandlingComponent.create(
                    handler,
                    ClasspathParameterResolverFactory.forClass(MethodLevelDisallowedHandler.class)
            );

            // when / then
            // No class-level @DisallowReplay, default behavior applies
            // Default EventHandlingComponent.supportsReset() returns false
            assertThat(testSubject.supportsReset()).isFalse();
        }

        @Test
        void fullyDisallowedReturnsFalse() {
            // given
            var handler = new FullyDisallowedHandler();
            var testSubject = AnnotatedEventHandlingComponent.create(
                    handler,
                    ClasspathParameterResolverFactory.forClass(FullyDisallowedHandler.class)
            );

            // when / then
            // Class has @DisallowReplay and no method has @AllowReplay
            // Wrapped with ReplayBlockingEventHandlingComponent which returns false
            assertThat(testSubject.supportsReset()).isFalse();
        }
    }

    private EventMessage stringEvent() {
        return new GenericEventMessage(new MessageType(String.class), "test-string");
    }

    private EventMessage longEvent() {
        return new GenericEventMessage(new MessageType(Long.class), 42L);
    }

    private ProcessingContext replayContext(EventMessage event) {
        return StubProcessingContext.withComponent(EventConverter.class, eventConverter())
                                    .withMessage(event)
                                    .withResource(TrackingToken.RESOURCE_KEY, replayToken);
    }

    private ProcessingContext regularContext(EventMessage event) {
        return StubProcessingContext.withComponent(EventConverter.class, eventConverter())
                                    .withMessage(event)
                                    .withResource(TrackingToken.RESOURCE_KEY, regularToken);
    }

    private static EventConverter eventConverter() {
        return new DelegatingEventConverter(PassThroughConverter.INSTANCE);
    }

    /**
     * Handler with class-level {@code @DisallowReplay} and method-level {@code @AllowReplay} override.
     */
    @DisallowReplay
    private static class ClassDisallowedWithMethodAllowedHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();

        @AllowReplay
        @EventHandler
        public void handle(String event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedStrings.add(event);
        }

        @EventHandler
        public void handle(Long event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedLongs.add(event);
        }
    }

    /**
     * Handler with method-level {@code @AllowReplay} and {@code @DisallowReplay}.
     */
    private static class MethodLevelDisallowedHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();

        @AllowReplay
        @EventHandler
        public void handle(String event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedStrings.add(event);
        }

        @EventHandler
        @DisallowReplay
        public void handle(Long event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedLongs.add(event);
        }
    }

    /**
     * Handler with class-level {@code @DisallowReplay} and no method-level overrides.
     */
    @DisallowReplay
    private static class FullyDisallowedHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();

        @EventHandler
        public void handle(String event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedStrings.add(event);
        }

        @EventHandler
        public void handle(Long event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedLongs.add(event);
        }
    }
}

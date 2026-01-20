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

package org.axonframework.messaging.eventhandling.replay;

import jakarta.annotation.Nonnull;
import org.axonframework.conversion.PassThroughConverter;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.AnnotationMessageTypeResolver;
import org.axonframework.messaging.core.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.core.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
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
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that the {@link AllowReplay} and {@link DisallowReplay} annotations have the expected behavior.
 * <p>
 * Tests validate:
 * <ul>
 *   <li>Class-level {@code @DisallowReplay} blocks all handlers during replay</li>
 *   <li>Method-level {@code @AllowReplay} can override class-level {@code @DisallowReplay}</li>
 *   <li>Method-level {@code @DisallowReplay} blocks individual handlers during replay</li>
 *   <li>{@code supportsReset()} returns appropriate values based on replay configuration</li>
 *   <li>Handlers receive unwrapped tracking tokens (not {@link ReplayToken})</li>
 * </ul>
 */
class ReplayAwareMessageHandlerWrapperTest {

    private static final TrackingToken regularToken = new GlobalSequenceTrackingToken(1L);
    private static final TrackingToken replayToken = ReplayToken.createReplayToken(regularToken);

    @Nested
    class GivenClassLevelDisallowReplayWithMethodLevelAllowReplay {

        private ClassDisallowedWithMethodAllowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new ClassDisallowedWithMethodAllowedHandler();
            testSubject = createTestSubjectFor(handler, ClassDisallowedWithMethodAllowedHandler.class);
        }

        @Test
        void allowReplayMethodHandlesEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
            assertThat(handler.receivedTokens).hasSize(1);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }

        @Test
        void disallowReplayMethodSkipsEventsDuringReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedLongs).isEmpty();
            assertThat(handler.receivedTokens).isEmpty();
        }

        @Test
        void disallowReplayMethodHandlesEventsOutsideReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, regularContext(longEvent));

            // then
            assertThat(handler.receivedLongs).containsExactly(42L);
            assertThat(handler.receivedTokens).hasSize(1);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }

        @Test
        void allowReplayMethodHandlesEventsOutsideReplay() {
            // given
            var stringEvent = stringEvent();

            // when
            testSubject.handle(stringEvent, regularContext(stringEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
            assertThat(handler.receivedTokens).hasSize(1);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }
    }

    @Nested
    class GivenMethodLevelDisallowReplay {

        private MethodLevelDisallowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new MethodLevelDisallowedHandler();
            testSubject = createTestSubjectFor(handler, MethodLevelDisallowedHandler.class);
        }

        @Test
        void allowReplayMethodHandlesEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
            assertThat(handler.receivedTokens).hasSize(1);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }

        @Test
        void disallowReplayMethodSkipsEventsDuringReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedLongs).isEmpty();
            assertThat(handler.receivedTokens).isEmpty();
        }

        @Test
        void disallowReplayMethodHandlesEventsOutsideReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, regularContext(longEvent));

            // then
            assertThat(handler.receivedLongs).containsExactly(42L);
            assertThat(handler.receivedTokens).hasSize(1);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }
    }

    @Nested
    class GivenClassLevelDisallowReplayWithoutOverrides {

        private FullyDisallowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new FullyDisallowedHandler();
            testSubject = createTestSubjectFor(handler, FullyDisallowedHandler.class);
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
            assertThat(handler.receivedTokens).isEmpty();
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
            assertThat(handler.receivedTokens).hasSize(2);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }
    }

    @Nested
    class GivenOnlyResetHandlerAllowsReplay {

        private OnlyResetHandlerAllowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new OnlyResetHandlerAllowedHandler();
            testSubject = createTestSubjectFor(handler, OnlyResetHandlerAllowedHandler.class);
        }

        @Test
        void resetHandlerIsInvokedOnReset() {
            // given
            var resetContext = new GenericResetContext(new MessageType(String.class), "reset-payload");
            var processingContext = StubProcessingContext.withComponent(EventConverter.class, eventConverter())
                                                         .withMessage(resetContext);

            // when
            testSubject.handle(resetContext, processingContext);

            // then
            assertThat(handler.resetInvoked).isTrue();
            assertThat(handler.resetPayload).isEqualTo("reset-payload");
        }

        @Test
        void eventHandlersSkipEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();
            var longEvent = longEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedStrings).isEmpty();
            assertThat(handler.receivedLongs).isEmpty();
            assertThat(handler.receivedTokens).isEmpty();
        }

        @Test
        void eventHandlersHandleEventsOutsideReplay() {
            // given
            var stringEvent = stringEvent();
            var longEvent = longEvent();

            // when
            testSubject.handle(stringEvent, regularContext(stringEvent));
            testSubject.handle(longEvent, regularContext(longEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
            assertThat(handler.receivedLongs).containsExactly(42L);
            assertThat(handler.receivedTokens).hasSize(2);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }
    }

    @Nested
    class GivenClassLevelAllowReplayWithAllMethodsDisallowed {

        private ClassAllowedWithAllMethodsDisallowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new ClassAllowedWithAllMethodsDisallowedHandler();
            testSubject = createTestSubjectFor(handler, ClassAllowedWithAllMethodsDisallowedHandler.class);
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
            assertThat(handler.receivedTokens).isEmpty();
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
            assertThat(handler.receivedTokens).hasSize(2);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }
    }

    @Nested
    class GivenClassLevelAllowReplayWithSomeMethodsDisallowed {

        private ClassAllowedWithSomeMethodsDisallowedHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new ClassAllowedWithSomeMethodsDisallowedHandler();
            testSubject = createTestSubjectFor(handler, ClassAllowedWithSomeMethodsDisallowedHandler.class);
        }

        @Test
        void allowReplayMethodHandlesEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
            assertThat(handler.receivedTokens).hasSize(1);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }

        @Test
        void disallowReplayMethodSkipsEventsDuringReplay() {
            // given
            var longEvent = longEvent();

            // when
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedLongs).isEmpty();
            assertThat(handler.receivedTokens).isEmpty();
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
            assertThat(handler.receivedTokens).hasSize(2);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }
    }

    @Nested
    class GivenNoReplayAnnotations {

        private NoAnnotationsHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new NoAnnotationsHandler();
            testSubject = createTestSubjectFor(handler, NoAnnotationsHandler.class);
        }

        @Test
        void handlesAllEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();
            var longEvent = longEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
            assertThat(handler.receivedLongs).containsExactly(42L);
            assertThat(handler.receivedTokens).hasSize(2);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
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
            assertThat(handler.receivedTokens).hasSize(2);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }
    }

    @Nested
    class GivenClassLevelAllowReplayOnly {

        private ClassAllowedOnlyHandler handler;
        private EventHandlingComponent testSubject;

        @BeforeEach
        void setUp() {
            handler = new ClassAllowedOnlyHandler();
            testSubject = createTestSubjectFor(handler, ClassAllowedOnlyHandler.class);
        }

        @Test
        void handlesAllEventsDuringReplay() {
            // given
            var stringEvent = stringEvent();
            var longEvent = longEvent();

            // when
            testSubject.handle(stringEvent, replayContext(stringEvent));
            testSubject.handle(longEvent, replayContext(longEvent));

            // then
            assertThat(handler.receivedStrings).containsExactly("test-string");
            assertThat(handler.receivedLongs).containsExactly(42L);
            assertThat(handler.receivedTokens).hasSize(2);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
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
            assertThat(handler.receivedTokens).hasSize(2);
            assertThat(handler.receivedTokens).noneMatch(ReplayToken.class::isInstance);
        }
    }

    @Nested
    class SupportsReset {

        @Test
        void classLevelDisallowReturnsTrueIfAnyMethodAllowsReplay() {
            // given
            var handler = new ClassDisallowedWithMethodAllowedHandler();
            var testSubject = createTestSubjectFor(handler, ClassDisallowedWithMethodAllowedHandler.class);

            // when / then
            assertThat(testSubject.supportsReset()).isTrue();
        }

        @Test
        void methodLevelDisallowWithoutClassLevelReturnsTrue() {
            // given
            var handler = new MethodLevelDisallowedHandler();
            var testSubject = createTestSubjectFor(handler, MethodLevelDisallowedHandler.class);

            // when / then
            assertThat(testSubject.supportsReset()).isTrue();
        }

        @Test
        void fullyDisallowedReturnsFalse() {
            // given
            var handler = new FullyDisallowedHandler();
            var testSubject = createTestSubjectFor(handler, FullyDisallowedHandler.class);

            // when / then
            assertThat(testSubject.supportsReset()).isFalse();
        }

        @Test
        void classDoesNotAllowReplayWithAllMethodsDisallowedReturnsFalse() {
            // given
            var handler = new ClassAllowedWithAllMethodsDisallowedHandler();
            var testSubject = createTestSubjectFor(handler, ClassAllowedWithAllMethodsDisallowedHandler.class);

            // when / then
            assertThat(testSubject.supportsReset()).isFalse();
        }

        @Test
        void classAllowReplayWithSomeMethodsDisallowedReturnsTrue() {
            // given
            var handler = new ClassAllowedWithSomeMethodsDisallowedHandler();
            var testSubject = createTestSubjectFor(handler, ClassAllowedWithSomeMethodsDisallowedHandler.class);

            // when / then
            assertThat(testSubject.supportsReset()).isTrue();
        }

        @Test
        void noAnnotationsReturnsTrue() {
            // given
            var handler = new NoAnnotationsHandler();
            var testSubject = createTestSubjectFor(handler, NoAnnotationsHandler.class);

            // when / then
            assertThat(testSubject.supportsReset()).isTrue();
        }

        @Test
        void classAllowReplayOnlyReturnsTrue() {
            // given
            var handler = new ClassAllowedOnlyHandler();
            var testSubject = createTestSubjectFor(handler, ClassAllowedOnlyHandler.class);

            // when / then
            assertThat(testSubject.supportsReset()).isTrue();
        }

        @Test
        void onlyResetHandlerAllowedReturnsFalse() {
            // given - all event handlers are @DisallowReplay, but @ResetHandler exists with @AllowReplay
            var handler = new OnlyResetHandlerAllowedHandler();
            var testSubject = createTestSubjectFor(handler, OnlyResetHandlerAllowedHandler.class);

            // when / then
            assertThat(testSubject.supportsReset()).isTrue();
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
        private final List<TrackingToken> receivedTokens = new ArrayList<>();

        @AllowReplay
        @EventHandler
        public void handle(String event, TrackingToken token) {
            receivedStrings.add(event);
            receivedTokens.add(token);
        }

        @EventHandler
        public void handle(Long event, TrackingToken token) {
            receivedLongs.add(event);
            receivedTokens.add(token);
        }
    }

    /**
     * Handler with method-level {@code @AllowReplay} and {@code @DisallowReplay}.
     */
    private static class MethodLevelDisallowedHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<TrackingToken> receivedTokens = new ArrayList<>();

        @AllowReplay
        @EventHandler
        public void handle(String event, TrackingToken token) {
            receivedStrings.add(event);
            receivedTokens.add(token);
        }

        @EventHandler
        @DisallowReplay
        public void handle(Long event, TrackingToken token) {
            receivedLongs.add(event);
            receivedTokens.add(token);
        }
    }

    /**
     * Handler with class-level {@code @DisallowReplay} and no method-level overrides.
     */
    @DisallowReplay
    private static class FullyDisallowedHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<TrackingToken> receivedTokens = new ArrayList<>();

        @EventHandler
        public void handle(String event, TrackingToken token) {
            receivedStrings.add(event);
            receivedTokens.add(token);
        }

        @EventHandler
        public void handle(Long event, TrackingToken token) {
            receivedLongs.add(event);
            receivedTokens.add(token);
        }
    }

    /**
     * Handler with class-level {@code @AllowReplay} but ALL methods have {@code @DisallowReplay}. This means no methods
     * actually support replay, so {@code supportsReset()} should return {@code false}.
     */
    @AllowReplay
    private static class ClassAllowedWithAllMethodsDisallowedHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<TrackingToken> receivedTokens = new ArrayList<>();

        @DisallowReplay
        @EventHandler
        public void handle(String event, TrackingToken token) {
            receivedStrings.add(event);
            receivedTokens.add(token);
        }

        @AllowReplay(false)
        @EventHandler
        public void handle(Long event, TrackingToken token) {
            receivedLongs.add(event);
            receivedTokens.add(token);
        }
    }

    /**
     * Handler with class-level {@code @AllowReplay} and only some methods have {@code @DisallowReplay}. Since at least
     * one method allows replay, {@code supportsReset()} should return {@code true}.
     */
    @AllowReplay
    private static class ClassAllowedWithSomeMethodsDisallowedHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<TrackingToken> receivedTokens = new ArrayList<>();

        @EventHandler
        public void handle(String event, TrackingToken token) {
            receivedStrings.add(event);
            receivedTokens.add(token);
        }

        @DisallowReplay
        @EventHandler
        public void handle(Long event, TrackingToken token) {
            receivedLongs.add(event);
            receivedTokens.add(token);
        }
    }

    /**
     * Handler with no replay annotations at all (default behavior). By default, all handlers allow replay, so
     * {@code supportsReset()} should return {@code true}.
     */
    private static class NoAnnotationsHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<TrackingToken> receivedTokens = new ArrayList<>();

        @EventHandler
        public void handle(String event, TrackingToken token) {
            receivedStrings.add(event);
            receivedTokens.add(token);
        }

        @EventHandler
        public void handle(Long event, TrackingToken token) {
            receivedLongs.add(event);
            receivedTokens.add(token);
        }
    }

    /**
     * Handler with only class-level {@code @AllowReplay} and no method-level annotations. All methods inherit the
     * class-level setting, so {@code supportsReset()} should return {@code true}.
     */
    @AllowReplay
    private static class ClassAllowedOnlyHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<TrackingToken> receivedTokens = new ArrayList<>();

        @EventHandler
        public void handle(String event, TrackingToken token) {
            receivedStrings.add(event);
            receivedTokens.add(token);
        }

        @EventHandler
        public void handle(Long event, TrackingToken token) {
            receivedLongs.add(event);
            receivedTokens.add(token);
        }
    }

    /**
     * Handler with class-level {@code @AllowReplay(false)} for all event handlers, but with a {@code @ResetHandler}.
     * The reset handler should still be invoked on reset (if annotated with {@code @AllowReplay}), even though all
     * event handlers are blocked during replay.
     */
    @AllowReplay(false)
    private static class OnlyResetHandlerAllowedHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<TrackingToken> receivedTokens = new ArrayList<>();
        private boolean resetInvoked = false;
        private Object resetPayload = null;

        @EventHandler
        public void handle(String event, TrackingToken token) {
            receivedStrings.add(event);
            receivedTokens.add(token);
        }

        @EventHandler
        public void handle(Long event, TrackingToken token) {
            receivedLongs.add(event);
            receivedTokens.add(token);
        }

        @AllowReplay
        @ResetHandler
        public void onReset(String payload) {
            resetInvoked = true;
            resetPayload = payload;
        }
    }

    @Nonnull
    private <T> AnnotatedEventHandlingComponent<T> createTestSubjectFor(T annotatedHandler,
                                                                        Class<T> annotatedHandlerClass) {
        return new AnnotatedEventHandlingComponent<>(
                annotatedHandler,
                ClasspathParameterResolverFactory.forClass(annotatedHandlerClass),
                ClasspathHandlerDefinition.forClass(annotatedHandlerClass),
                new AnnotationMessageTypeResolver(),
                new DelegatingEventConverter(PassThroughConverter.INSTANCE)
        );
    }
}

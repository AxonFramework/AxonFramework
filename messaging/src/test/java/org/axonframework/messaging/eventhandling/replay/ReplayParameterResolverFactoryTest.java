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
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that the {@link ReplayStatus} parameter is correctly resolved during event handling.
 * <p>
 * Tests validate:
 * <ul>
 *   <li>{@link ReplayStatus} parameter is resolved correctly during replay</li>
 *   <li>{@link ReplayStatus} parameter is resolved correctly outside of replay</li>
 *   <li>Handlers receive unwrapped tracking tokens (not {@link ReplayToken})</li>
 * </ul>
 */
class ReplayParameterResolverFactoryTest {

    private static final TrackingToken regularToken = new GlobalSequenceTrackingToken(1L);
    private static final TrackingToken replayToken = ReplayToken.createReplayToken(regularToken);

    private SomeHandler handler;
    private EventHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        testSubject = new AnnotatedEventHandlingComponent<>(
                handler,
                ClasspathParameterResolverFactory.forClass(SomeHandler.class),
                ClasspathHandlerDefinition.forClass(SomeHandler.class),
                new AnnotationMessageTypeResolver(),
                new DelegatingEventConverter(PassThroughConverter.INSTANCE)
        );
    }

    @Test
    void replayStatusIsReplayDuringReplay() {
        // given
        var event = longEvent(1L);

        // when
        testSubject.handle(event, replayContext(event));

        // then
        assertThat(handler.receivedLongs).containsExactly(1L);
        assertThat(handler.receivedInReplay).containsExactly(1L);
    }

    @Test
    void replayStatusIsNotReplayOutsideReplay() {
        // given
        var event = longEvent(2L);

        // when
        testSubject.handle(event, regularContext(event));

        // then
        assertThat(handler.receivedLongs).containsExactly(2L);
        assertThat(handler.receivedInReplay).isEmpty();
    }

    @Test
    void invokeWithReplayAndRegularTokens() {
        // given
        var replayEvent = longEvent(1L);
        var liveEvent = longEvent(2L);

        // when
        testSubject.handle(replayEvent, replayContext(replayEvent));
        testSubject.handle(liveEvent, regularContext(liveEvent));

        // then
        assertThat(handler.receivedLongs).containsExactly(1L, 2L);
        assertThat(handler.receivedInReplay).containsExactly(1L);
    }

    private EventMessage longEvent(Long value) {
        return new GenericEventMessage(new MessageType(Long.class), value);
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

    private static class SomeHandler {

        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<Long> receivedInReplay = new ArrayList<>();

        @EventHandler
        public void handle(Long event, TrackingToken token, ReplayStatus replayStatus) {
            assertFalse(token instanceof ReplayToken);
            receivedLongs.add(event);
            if (replayStatus.isReplay()) {
                receivedInReplay.add(event);
            }
        }
    }
}

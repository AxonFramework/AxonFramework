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

import org.axonframework.messaging.eventhandling.annotation.AnnotatedEventHandlingComponent;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventhandling.replay.annotation.AllowReplay;
import org.axonframework.messaging.eventhandling.replay.annotation.DisallowReplay;
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test in order to verify that the {@link DisallowReplay} annotation has the expected behaviour.
 */
@Disabled("TODO #3304")
class ReplayAwareMessageHandlerWrapperWithDisallowReplayTest {

    private SomeHandler handler;
    private SomeMethodHandler methodHandler;
    private AnnotatedEventHandlingComponent testSubject;
    private AnnotatedEventHandlingComponent testMethodSubject;
    private TrackingToken replayToken;
    private AnnotatedEventHandlingComponent testDisallowingSubject;
    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        ReplayPreventingHandler disallowingHandler = new ReplayPreventingHandler();
        methodHandler = new SomeMethodHandler();
//        testSubject = new AnnotationEventHandlerAdapter(handler, messageTypeResolver);
//        testMethodSubject = new AnnotationEventHandlerAdapter(methodHandler, messageTypeResolver);
//        testDisallowingSubject = new AnnotationEventHandlerAdapter(disallowingHandler, messageTypeResolver);
        replayToken = ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(1L));
    }

//    @Test
//    void invokeWithReplayTokens() throws Exception {
//        EventMessage stringEvent = asEventMessage("1");
//        EventMessage longEvent = new GenericTrackedEventMessage(replayToken, asEventMessage(1L));
//        ProcessingContext stringContext = StubProcessingContext.forMessage(stringEvent)
//                                                               .withResource(TrackingToken.RESOURCE_KEY, replayToken);
//        ProcessingContext longContext = StubProcessingContext.forMessage(longEvent)
//                                                             .withResource(TrackingToken.RESOURCE_KEY, replayToken);
//
//        assertTrue(testSubject.canHandle(stringEvent, stringContext));
//        assertTrue(testMethodSubject.canHandle(stringEvent, stringContext));
//        assertTrue(testSubject.canHandle(longEvent, longContext));
//        assertTrue(testMethodSubject.canHandle(longEvent, longContext));
//        testSubject.handleSync(stringEvent, stringContext);
//        testMethodSubject.handleSync(stringEvent, stringContext);
//        testSubject.handleSync(longEvent, longContext);
//        testMethodSubject.handleSync(longEvent, longContext);
//
//        assertTrue(handler.receivedLongs.isEmpty());
//        assertTrue(methodHandler.receivedLongs.isEmpty());
//        assertFalse(handler.receivedStrings.isEmpty());
//        assertFalse(methodHandler.receivedStrings.isEmpty());
//
//        assertTrue(testSubject.supportsReset());
//        assertTrue(testMethodSubject.supportsReset());
//        assertFalse(testDisallowingSubject.supportsReset());
//    }

    @DisallowReplay
    private static class SomeHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();

        @AllowReplay
        @EventHandler
        public void handle(String event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedStrings.add(event);
        }

        @EventHandler()
        public void handle(Long event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedLongs.add(event);
        }
    }

    private static class SomeMethodHandler {

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

    @DisallowReplay
    private static class ReplayPreventingHandler {

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

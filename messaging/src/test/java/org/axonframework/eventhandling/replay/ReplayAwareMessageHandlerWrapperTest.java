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

package org.axonframework.eventhandling.replay;

import org.axonframework.eventhandling.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.processors.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.ReplayToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.axonframework.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

class ReplayAwareMessageHandlerWrapperTest {

    private SomeHandler handler;
    private AnnotationEventHandlerAdapter testSubject;
    private TrackingToken replayToken;
    private AnnotationEventHandlerAdapter testDisallowingSubject;
    private SomeMethodHandler methodHandler;
    private AnnotationEventHandlerAdapter testMethodSubject;
    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler, messageTypeResolver);

        methodHandler = new SomeMethodHandler();
        testMethodSubject = new AnnotationEventHandlerAdapter(methodHandler, messageTypeResolver);

        ReplayPreventingHandler disallowingHandler = new ReplayPreventingHandler();
        testDisallowingSubject = new AnnotationEventHandlerAdapter(disallowingHandler, messageTypeResolver);

        GlobalSequenceTrackingToken regularToken = new GlobalSequenceTrackingToken(1L);
        replayToken = ReplayToken.createReplayToken(regularToken);
    }

    @Test
    void invokeWithReplayTokens() throws Exception {
        GenericTrackedEventMessage stringEvent = new GenericTrackedEventMessage(replayToken,
                                                                                          asEventMessage("1"));
        ProcessingContext stringContext = StubProcessingContext.forMessage(stringEvent);
        GenericTrackedEventMessage longEvent = new GenericTrackedEventMessage(replayToken,
                                                                                        asEventMessage(1L));
        ProcessingContext longContext = StubProcessingContext.forMessage(longEvent);
        assertTrue(testSubject.canHandle(stringEvent, stringContext));
        assertTrue(testMethodSubject.canHandle(stringEvent, stringContext));
        assertTrue(testSubject.canHandle(longEvent, longContext));
        assertTrue(testMethodSubject.canHandle(longEvent, longContext));
        testSubject.handleSync(stringEvent, stringContext);
        testMethodSubject.handleSync(stringEvent, stringContext);
        testSubject.handleSync(longEvent, longContext);
        testMethodSubject.handleSync(longEvent, longContext);

        assertTrue(handler.receivedLongs.isEmpty());
        assertTrue(methodHandler.receivedLongs.isEmpty());
        assertFalse(handler.receivedStrings.isEmpty());
        assertFalse(methodHandler.receivedStrings.isEmpty());

        assertTrue(testSubject.supportsReset());
        assertTrue(testMethodSubject.supportsReset());
        assertFalse(testDisallowingSubject.supportsReset());
    }

    @AllowReplay(false)
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
        @AllowReplay(false)
        public void handle(Long event, TrackingToken token) {
            assertFalse(token instanceof ReplayToken);
            receivedLongs.add(event);
        }
    }

    @AllowReplay(false)
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

/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.eventhandling.AllowReplay;
import org.axonframework.eventhandling.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.DisallowReplay;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackingToken;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test in order to verify that the {@link DisallowReplay} annotation has the expected behaviour.
 */
class ReplayAwareMessageHandlerWrapperWithDisallowReplayTest {

    private SomeHandler handler;
    private SomeMethodHandler methodHandler;
    private AnnotationEventHandlerAdapter testSubject;
    private AnnotationEventHandlerAdapter testMethodSubject;
    private ReplayPreventingHandler disallowingHandler;
    private ReplayToken replayToken;
    private AnnotationEventHandlerAdapter testDisallowingSubject;

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        disallowingHandler = new ReplayPreventingHandler();
        methodHandler = new SomeMethodHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler);
        testMethodSubject = new AnnotationEventHandlerAdapter(methodHandler);
        testDisallowingSubject = new AnnotationEventHandlerAdapter(disallowingHandler);
        replayToken = new ReplayToken(new GlobalSequenceTrackingToken(1L));
    }

    @Test
    void invokeWithReplayTokens() throws Exception {
        GenericTrackedEventMessage<Object> stringEvent = new GenericTrackedEventMessage<>(replayToken, asEventMessage("1"));
        GenericTrackedEventMessage<Object> longEvent = new GenericTrackedEventMessage<>(replayToken, asEventMessage(1L));
        assertTrue(testSubject.canHandle(stringEvent));
        assertTrue(testMethodSubject.canHandle(stringEvent));
        assertTrue(testSubject.canHandle(longEvent));
        assertTrue(testMethodSubject.canHandle(longEvent));
        testSubject.handle(stringEvent);
        testMethodSubject.handle(stringEvent);
        testSubject.handle(longEvent);
        testMethodSubject.handle(longEvent);

        assertTrue(handler.receivedLongs.isEmpty());
        assertTrue(methodHandler.receivedLongs.isEmpty());
        assertFalse(handler.receivedStrings.isEmpty());
        assertFalse(methodHandler.receivedStrings.isEmpty());

        assertTrue(testSubject.supportsReset());
        assertTrue(testMethodSubject.supportsReset());
        assertFalse(testDisallowingSubject.supportsReset());

    }

    @DisallowReplay
    private static class SomeHandler {

        private List<String> receivedStrings = new ArrayList<>();
        private List<Long> receivedLongs = new ArrayList<>();

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

        private List<String> receivedStrings = new ArrayList<>();
        private List<Long> receivedLongs = new ArrayList<>();

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

        private List<String> receivedStrings = new ArrayList<>();
        private List<Long> receivedLongs = new ArrayList<>();

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

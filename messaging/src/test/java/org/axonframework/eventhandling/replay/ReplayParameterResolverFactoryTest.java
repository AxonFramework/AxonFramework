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
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.ReplayStatus;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.axonframework.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

class ReplayParameterResolverFactoryTest {

    private SomeHandler handler;
    private AnnotationEventHandlerAdapter testSubject;
    private TrackingToken replayToken;
    private GlobalSequenceTrackingToken regularToken;
    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler, messageTypeResolver);
        regularToken = new GlobalSequenceTrackingToken(1L);
        replayToken = ReplayToken.createReplayToken(regularToken);
    }

    @Test
    void invokeWithReplayTokens() throws Exception {
        GenericTrackedEventMessage replayEvent = new GenericTrackedEventMessage(replayToken, asEventMessage(1L));
        ProcessingContext replayContext = StubProcessingContext.forMessage(replayEvent);
        GenericTrackedEventMessage liveEvent = new GenericTrackedEventMessage(regularToken, asEventMessage(2L));
        ProcessingContext liveContext = StubProcessingContext.forMessage(liveEvent);
        assertTrue(testSubject.canHandle(replayEvent, replayContext));
        assertTrue(testSubject.canHandle(liveEvent, liveContext));
        testSubject.handleSync(replayEvent, replayContext);
        testSubject.handleSync(liveEvent, liveContext);

        assertEquals(asList(1L, 2L), handler.receivedLongs);
        assertEquals(singletonList(1L), handler.receivedInReplay);
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

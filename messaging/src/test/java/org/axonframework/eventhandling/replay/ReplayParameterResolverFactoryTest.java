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
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.ReplayStatus;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.ClassBasedMessageNameResolver;
import org.axonframework.messaging.MessageNameResolver;
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
    private final MessageNameResolver messageNameResolver = new ClassBasedMessageNameResolver();

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler, messageNameResolver);
        regularToken = new GlobalSequenceTrackingToken(1L);
        replayToken = ReplayToken.createReplayToken(regularToken);
    }

    @Test
    void invokeWithReplayTokens() throws Exception {
        GenericTrackedEventMessage<Object> replayEvent = new GenericTrackedEventMessage<>(replayToken, asEventMessage(1L));
        GenericTrackedEventMessage<Object> liveEvent = new GenericTrackedEventMessage<>(regularToken, asEventMessage(2L));
        assertTrue(testSubject.canHandle(replayEvent));
        assertTrue(testSubject.canHandle(liveEvent));
        testSubject.handleSync(replayEvent);
        testSubject.handleSync(liveEvent);

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

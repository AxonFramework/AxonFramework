/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

class ReplayContextParameterResolverFactoryTest {

    private final MyResetContext resetContext = new MyResetContext(asList(2L, 3L));
    private SomeHandler handler;
    private AnnotationEventHandlerAdapter testSubject;
    private ReplayToken replayToken;
    private GlobalSequenceTrackingToken regularToken;

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler);
        regularToken = new GlobalSequenceTrackingToken(1L);
        replayToken = (ReplayToken) ReplayToken.createReplayToken(regularToken,
                                                                  new GlobalSequenceTrackingToken(0),
                                                                  resetContext);
    }

    @Test
    void testResolvesIfMatching() throws Exception {
        GenericTrackedEventMessage<Object> replayEvent = new GenericTrackedEventMessage<>(replayToken,
                                                                                          asEventMessage(1L));
        GenericTrackedEventMessage<Object> liveEvent = new GenericTrackedEventMessage<>(regularToken,
                                                                                        asEventMessage(2L));
        assertTrue(testSubject.canHandle(replayEvent));
        assertTrue(testSubject.canHandle(liveEvent));
        testSubject.handle(createMessage(1L, true));
        testSubject.handle(createMessage(2L, true));
        testSubject.handle(createMessage(3L, true));
        testSubject.handle(createMessage(4L, false));

        assertEquals(asList(1L, 2L, 3L, 4L), handler.receivedLongs);
        assertEquals(asList(2L, 3L), handler.receivedInReplay);
    }

    private TrackedEventMessage<Object> createMessage(Long position, boolean replay) {
        return new GenericTrackedEventMessage<>(createToken(position, replay), asEventMessage(position));
    }

    private TrackingToken createToken(Long position, boolean replay) {
        if (replay) {
            return ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(position + 1),
                                                 new GlobalSequenceTrackingToken(position),
                                                 resetContext);
        }
        return new GlobalSequenceTrackingToken(position);
    }

    private static class SomeHandler {

        private List<Long> receivedLongs = new ArrayList<>();
        private List<Long> receivedInReplay = new ArrayList<>();

        @EventHandler
        public void handle(Long event, TrackingToken token, @ReplayContext MyResetContext resetContext) {
            receivedLongs.add(event);

            long position = token.position().orElse(0);
            boolean tokenMatchesFilter = resetContext != null && resetContext.sequences.contains(position);
            if (tokenMatchesFilter) {
                receivedInReplay.add(event);
            }
        }
    }

    private static class MyResetContext {

        private final List<Long> sequences;

        public MyResetContext(final List<Long> sequences) {
            this.sequences = sequences;
        }
    }
}

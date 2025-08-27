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
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.axonframework.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

class ReplayContextParameterResolverFactoryTest {

    private final MyResetContext resetContext = new MyResetContext(asList(2L, 3L));
    private SomeHandler handler;
    private AnnotationEventHandlerAdapter testSubject;
    private ReplayToken replayToken;
    private GlobalSequenceTrackingToken regularToken;
    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler, messageTypeResolver);
        regularToken = new GlobalSequenceTrackingToken(1L);
        replayToken = (ReplayToken) ReplayToken.createReplayToken(regularToken,
                                                                  new GlobalSequenceTrackingToken(0),
                                                                  resetContext);
    }

    @Test
    void resolvesIfMatching() throws Exception {
        GenericTrackedEventMessage replayEvent = new GenericTrackedEventMessage(replayToken,
                                                                                          asEventMessage(1L));
        ProcessingContext replayEventContext = StubProcessingContext.forMessage(replayEvent);
        GenericTrackedEventMessage liveEvent = new GenericTrackedEventMessage(regularToken,
                                                                                        asEventMessage(2L));
        ProcessingContext liveEventContext = StubProcessingContext.forMessage(liveEvent);

        assertTrue(testSubject.canHandle(replayEvent, replayEventContext));
        assertTrue(testSubject.canHandle(liveEvent, liveEventContext));
        TrackedEventMessage event1 = createMessage(1L, true);
        ProcessingContext event1Context = StubProcessingContext.forMessage(event1);
        testSubject.handleSync(event1, event1Context);
        TrackedEventMessage event2 = createMessage(2L, true);
        ProcessingContext event2Context = StubProcessingContext.forMessage(event2);
        testSubject.handleSync(event2, event2Context);
        TrackedEventMessage event3 = createMessage(3L, true);
        ProcessingContext event3Context = StubProcessingContext.forMessage(event3);
        testSubject.handleSync(event3, event3Context);
        TrackedEventMessage event4 = createMessage(4L, false);
        ProcessingContext event4Context = StubProcessingContext.forMessage(event4);
        testSubject.handleSync(event4, event4Context);

        assertEquals(asList(1L, 2L, 3L, 4L), handler.receivedLongs);
        assertEquals(asList(2L, 3L), handler.receivedInReplay);
    }

    private TrackedEventMessage createMessage(Long position, boolean replay) {
        return new GenericTrackedEventMessage(createToken(position, replay), asEventMessage(position));
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

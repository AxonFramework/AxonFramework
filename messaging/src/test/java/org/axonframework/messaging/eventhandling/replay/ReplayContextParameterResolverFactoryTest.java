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
import org.axonframework.messaging.eventhandling.replay.annotation.ReplayContext;
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

@Disabled("#3304")
class ReplayContextParameterResolverFactoryTest {

    //    private final MyResetContext resetContext = new MyResetContext(asList(2L, 3L));
//    private SomeHandler handler;
    private AnnotatedEventHandlingComponent<SomeHandler> testSubject;
    private ReplayToken replayToken;
    private GlobalSequenceTrackingToken regularToken;
    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @BeforeEach
    void setUp() {
//        handler = new SomeHandler();
//        testSubject = new AnnotationEventHandlerAdapter(handler, messageTypeResolver);
//        regularToken = new GlobalSequenceTrackingToken(1L);
//        replayToken = (ReplayToken) ReplayToken.createReplayToken(regularToken,
//                                                                  new GlobalSequenceTrackingToken(0),
//                                                                  resetContext);
    }

    //    @Test
//    void resolvesIfMatching() throws Exception {
//        EventMessage replayEvent = new GenericTrackedEventMessage(replayToken,
//                                                                  asEventMessage(1L));
//        ProcessingContext replayContext = StubProcessingContext.forMessage(replayEvent)
//                                                               .withResource(RESOURCE_KEY, replayToken);
//        EventMessage liveEvent = new GenericTrackedEventMessage(regularToken,
//                                                                asEventMessage(2L));
//        ProcessingContext liveContext = StubProcessingContext.forMessage(liveEvent)
//                                                             .withResource(RESOURCE_KEY, regularToken);
//
//        assertTrue(testSubject.canHandle(replayEvent, replayContext));
//        assertTrue(testSubject.canHandle(liveEvent, liveContext));
//        EventMessage event1 = asEventMessage(1L);
//        ProcessingContext event1Context = StubProcessingContext.forMessage(event1)
//                                                               .withResource(RESOURCE_KEY, createToken(1L, true));
//        testSubject.handleSync(event1, event1Context);
//        EventMessage event2 = asEventMessage(2L);
//        ProcessingContext event2Context = StubProcessingContext.forMessage(event2)
//                                                               .withResource(RESOURCE_KEY, createToken(2L, true));
//        testSubject.handleSync(event2, event2Context);
//        EventMessage event3 = asEventMessage(3L);
//        ProcessingContext event3Context = StubProcessingContext.forMessage(event3)
//                                                               .withResource(RESOURCE_KEY, createToken(3L, true));
//        testSubject.handleSync(event3, event3Context);
//        EventMessage event4 = asEventMessage(4L);
//        ProcessingContext event4Context = StubProcessingContext.forMessage(event4)
//                                                               .withResource(RESOURCE_KEY, createToken(4L, true));
//        testSubject.handleSync(event4, event4Context);
//
//        assertEquals(asList(1L, 2L, 3L, 4L), handler.receivedLongs);
//        assertEquals(asList(2L, 3L), handler.receivedInReplay);
//    }
//
//    private TrackedEventMessage createMessage(Long position, boolean replay) {
//        return new GenericTrackedEventMessage(createToken(position, replay), asEventMessage(position));
//    }
//
//    private TrackingToken createToken(Long position, boolean replay) {
//        if (replay) {
//            return ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(position + 1),
//                                                 new GlobalSequenceTrackingToken(position),
//                                                 resetContext);
//        }
//        return new GlobalSequenceTrackingToken(position);
//    }
//
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

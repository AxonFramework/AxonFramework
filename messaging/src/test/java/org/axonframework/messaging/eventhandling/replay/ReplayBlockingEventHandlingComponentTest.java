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
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link ReplayBlockingEventHandlingComponent}.
 * <p>
 * Tests validate:
 * <ul>
 *   <li>Events are blocked during replay</li>
 *   <li>Events are handled normally outside replay</li>
 *   <li>{@code supportsReset()} returns appropriate values based on reset handler registration</li>
 *   <li>Reset handlers are properly delegated</li>
 * </ul>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class ReplayBlockingEventHandlingComponentTest {

    private static final TrackingToken regularToken = new GlobalSequenceTrackingToken(1L);
    private static final TrackingToken replayToken = ReplayToken.createReplayToken(regularToken);

    private List<String> receivedEvents;
    private ReplayBlockingEventHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        receivedEvents = new ArrayList<>();
        EventHandlingComponent delegate = new SimpleEventHandlingComponent();
        delegate.subscribe(
                new QualifiedName(String.class),
                (event, context) -> {
                    receivedEvents.add(event.payloadAs(String.class));
                    return MessageStream.empty();
                }
        );
        testSubject = new ReplayBlockingEventHandlingComponent(delegate);
    }

    @Nested
    class EventHandling {

        @Test
        void blocksEventsDuringReplay() {
            // given
            var event = stringEvent("replay-event");

            // when
            testSubject.handle(event, replayContext(event));

            // then
            assertThat(receivedEvents).isEmpty();
        }

        @Test
        void handlesEventsOutsideReplay() {
            // given
            var event = stringEvent("regular-event");

            // when
            testSubject.handle(event, regularContext(event));

            // then
            assertThat(receivedEvents).containsExactly("regular-event");
        }

        @Test
        void handlesEventsWhenNoTrackingToken() {
            // given
            var event = stringEvent("no-token-event");
            var context = StubProcessingContext.withComponent(EventConverter.class, eventConverter())
                                               .withMessage(event);

            // when
            testSubject.handle(event, context);

            // then
            assertThat(receivedEvents).containsExactly("no-token-event");
        }

        @Test
        void blocksMultipleEventsDuringReplay() {
            // given
            var event1 = stringEvent("event-1");
            var event2 = stringEvent("event-2");
            var event3 = stringEvent("event-3");

            // when
            testSubject.handle(event1, replayContext(event1));
            testSubject.handle(event2, replayContext(event2));
            testSubject.handle(event3, regularContext(event3));

            // then - only event3 (outside replay) should be received
            assertThat(receivedEvents).containsExactly("event-3");
        }
    }

    @Nested
    class SupportsReset {

        @Test
        void returnsFalseWhenNoResetHandlerRegistered() {
            // when / then
            assertThat(testSubject.supportsReset()).isFalse();
        }

        @Test
        void returnsTrueWhenResetHandlerRegistered() {
            // given
            testSubject.subscribe((resetContext, context) -> MessageStream.empty());

            // when / then
            assertThat(testSubject.supportsReset()).isTrue();
        }

        @Test
        void returnsTrueAfterMultipleResetHandlersRegistered() {
            // given
            testSubject.subscribe((resetContext, context) -> MessageStream.empty());
            testSubject.subscribe((resetContext, context) -> MessageStream.empty());

            // when / then
            assertThat(testSubject.supportsReset()).isTrue();
        }
    }

    @Nested
    class ResetHandlerDelegation {

        @Test
        void delegatesResetHandlerToDelegate() {
            // given
            AtomicBoolean resetHandlerInvoked = new AtomicBoolean(false);
            testSubject.subscribe((resetContext, context) -> {
                resetHandlerInvoked.set(true);
                return MessageStream.empty();
            });

            var resetContext = new GenericResetContext(new MessageType(String.class), "reset-payload");
            var processingContext = StubProcessingContext.withComponent(EventConverter.class, eventConverter())
                                                         .withMessage(resetContext);

            // when
            testSubject.handle(resetContext, processingContext);

            // then
            assertThat(resetHandlerInvoked).isTrue();
        }

        @Test
        void resetHandlerReceivesCorrectPayload() {
            // given
            List<Object> receivedPayloads = new ArrayList<>();
            testSubject.subscribe((resetContext, context) -> {
                receivedPayloads.add(resetContext.payloadAs(String.class, PassThroughConverter.INSTANCE));
                return MessageStream.empty();
            });

            var resetContext = new GenericResetContext(new MessageType(String.class), "my-reset-payload");
            var processingContext = StubProcessingContext.withComponent(EventConverter.class, eventConverter())
                                                         .withMessage(resetContext);

            // when
            testSubject.handle(resetContext, processingContext);

            // then
            assertThat(receivedPayloads).containsExactly("my-reset-payload");
        }
    }

    private EventMessage stringEvent(String payload) {
        return new GenericEventMessage(new MessageType(String.class), payload);
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
}

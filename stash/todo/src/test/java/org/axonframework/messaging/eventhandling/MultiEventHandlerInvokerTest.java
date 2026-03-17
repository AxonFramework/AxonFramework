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

package org.axonframework.messaging.eventhandling;

import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MultiEventHandlerInvoker}.
 *
 * @author Steven van Beelen
 */
@Disabled("Kept for checking with old approach mostly, to see what we can carry over")
class MultiEventHandlerInvokerTest {

    private static final Object NO_RESET_PAYLOAD = null;

    private MultiEventHandlerInvoker testSubject;

    private final org.axonframework.messaging.eventhandling.EventHandlerInvoker mockedEventHandlerInvokerOne = mock(org.axonframework.messaging.eventhandling.EventHandlerInvoker.class);
    private final org.axonframework.messaging.eventhandling.EventHandlerInvoker mockedEventHandlerInvokerTwo = mock(org.axonframework.messaging.eventhandling.EventHandlerInvoker.class);

    private EventMessage testEventMessage;
    private ProcessingContext context;
    private EventMessage replayMessage;
    private Segment testSegment;

    @BeforeEach
    void setUp() {
        testEventMessage = EventTestUtils.asEventMessage("some-event");
        context = StubProcessingContext.forMessage(testEventMessage);
        TrackingToken testToken = ReplayToken.createReplayToken(
                new GlobalSequenceTrackingToken(10), new GlobalSequenceTrackingToken(0)
        );
        replayMessage = new GenericTrackedEventMessage(testToken, EventTestUtils.asEventMessage("replay-event"));
        testSegment = new Segment(1, 1);

        when(mockedEventHandlerInvokerOne.canHandle(any(), any(), eq(testSegment))).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.canHandle(any(), any(), eq(testSegment))).thenReturn(true);

        testSubject = new MultiEventHandlerInvoker(mockedEventHandlerInvokerOne, mockedEventHandlerInvokerTwo);
    }

    @Test
    void delegatesReturnsSetDelegates() {
        List<EventHandlerInvoker> result = testSubject.delegates();

        assertTrue(result.contains(mockedEventHandlerInvokerOne));
        assertTrue(result.contains(mockedEventHandlerInvokerTwo));
    }

    @Test
    void canHandleCallsCanHandleOnTheFirstDelegateToReturn() {
        testSubject.canHandle(testEventMessage, context, testSegment);

        verify(mockedEventHandlerInvokerOne).canHandle(testEventMessage, context, testSegment);
        verifyNoInteractions(mockedEventHandlerInvokerTwo);
    }

    @Test
    void handleCallsCanHandleAndHandleOfAllDelegates() throws Exception {
        testSubject.handle(testEventMessage, context, testSegment);

        verify(mockedEventHandlerInvokerOne).canHandle(testEventMessage, context, testSegment);
        verify(mockedEventHandlerInvokerOne).handle(testEventMessage, context, testSegment);
        verify(mockedEventHandlerInvokerTwo).canHandle(testEventMessage, context, testSegment);
        verify(mockedEventHandlerInvokerTwo).handle(testEventMessage, context, testSegment);
    }

    @Test
    void handleThrowsExceptionIfDelegatesThrowAnException() throws Exception {
        doThrow(new RuntimeException()).when(mockedEventHandlerInvokerTwo).handle(testEventMessage, null, testSegment);

        assertThrows(RuntimeException.class, () -> testSubject.handle(testEventMessage, null, testSegment));
    }

    @Test
    void supportResetWhenAllSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(true);

        assertTrue(testSubject.supportsReset());
    }

    @Test
    void supportResetWhenSomeSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        assertTrue(testSubject.supportsReset());
    }

    @Test
    void supportResetWhenNoneSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(false);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        assertFalse(testSubject.supportsReset());
    }

    @Test
    void performReset() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        testSubject.performReset(null);

        verify(mockedEventHandlerInvokerOne, times(1)).performReset(eq(NO_RESET_PAYLOAD), isNull());
        verify(mockedEventHandlerInvokerTwo, never()).performReset(eq(NO_RESET_PAYLOAD), isNull());
    }

    @Test
    void performResetWithResetContext() {
        String resetContext = "reset-context";

        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        testSubject.performReset(resetContext, null);

        verify(mockedEventHandlerInvokerOne, times(1)).performReset(eq(resetContext), isNull());
        verify(mockedEventHandlerInvokerTwo, never()).performReset(eq(resetContext), isNull());
    }

    @Test
    void invokersNotSupportingResetDoNotReceiveRedeliveries() throws Exception {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        assertTrue(testSubject.canHandle(testEventMessage, context, testSegment));
        testSubject.handle(testEventMessage, context, testSegment);
        testSubject.handle(replayMessage, context, testSegment);

        InOrder inOrder = inOrder(mockedEventHandlerInvokerOne, mockedEventHandlerInvokerTwo);
        inOrder.verify(mockedEventHandlerInvokerOne).handle(testEventMessage, context, testSegment);
        inOrder.verify(mockedEventHandlerInvokerTwo).handle(testEventMessage, context, testSegment);
        inOrder.verify(mockedEventHandlerInvokerOne).handle(replayMessage, context, testSegment);

        verify(mockedEventHandlerInvokerTwo, never()).handle(eq(replayMessage), isNull(), any());
    }

    @Test
    void performResetThrowsException() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);
        doThrow(RuntimeException.class).when(mockedEventHandlerInvokerOne).performReset(any(), isNull());

        assertThrows(Exception.class, () -> testSubject.performReset(null));
    }
}

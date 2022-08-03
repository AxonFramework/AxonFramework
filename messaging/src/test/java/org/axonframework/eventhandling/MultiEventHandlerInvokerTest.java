/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling;

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
class MultiEventHandlerInvokerTest {

    private static final Object NO_RESET_PAYLOAD = null;

    private MultiEventHandlerInvoker testSubject;

    private final EventHandlerInvoker mockedEventHandlerInvokerOne = mock(EventHandlerInvoker.class);
    private final EventHandlerInvoker mockedEventHandlerInvokerTwo = mock(EventHandlerInvoker.class);

    private EventMessage<String> testEventMessage;
    private EventMessage<String> replayMessage;
    private Segment testSegment;

    @BeforeEach
    void setUp() {
        testEventMessage = GenericEventMessage.asEventMessage("some-event");
        replayMessage = new GenericTrackedEventMessage<>(new ReplayToken(new GlobalSequenceTrackingToken(10),
                                                                         new GlobalSequenceTrackingToken(0)),
                                                         GenericEventMessage.asEventMessage("replay-event"));
        testSegment = new Segment(1, 1);

        when(mockedEventHandlerInvokerOne.canHandle(any(), eq(testSegment))).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.canHandle(any(), eq(testSegment))).thenReturn(true);

        testSubject = new MultiEventHandlerInvoker(mockedEventHandlerInvokerOne, mockedEventHandlerInvokerTwo);
    }

    @Test
    void testDelegatesReturnsSetDelegates() {
        List<EventHandlerInvoker> result = testSubject.delegates();

        assertTrue(result.contains(mockedEventHandlerInvokerOne));
        assertTrue(result.contains(mockedEventHandlerInvokerTwo));
    }

    @Test
    void testCanHandleCallsCanHandleOnTheFirstDelegateToReturn() {
        testSubject.canHandle(testEventMessage, testSegment);

        verify(mockedEventHandlerInvokerOne).canHandle(testEventMessage, testSegment);
        verifyNoInteractions(mockedEventHandlerInvokerTwo);
    }

    @Test
    void testHandleCallsCanHandleAndHandleOfAllDelegates() throws Exception {
        testSubject.handle(testEventMessage, testSegment);

        verify(mockedEventHandlerInvokerOne).canHandle(testEventMessage, testSegment);
        verify(mockedEventHandlerInvokerOne).handle(testEventMessage, testSegment);
        verify(mockedEventHandlerInvokerTwo).canHandle(testEventMessage, testSegment);
        verify(mockedEventHandlerInvokerTwo).handle(testEventMessage, testSegment);
    }

    @Test
    void testHandleThrowsExceptionIfDelegatesThrowAnException() throws Exception {
        doThrow(new RuntimeException()).when(mockedEventHandlerInvokerTwo).handle(testEventMessage, testSegment);

        assertThrows(RuntimeException.class, () -> testSubject.handle(testEventMessage, testSegment));
    }

    @Test
    void testSupportResetWhenAllSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(true);

        assertTrue(testSubject.supportsReset());
    }

    @Test
    void testSupportResetWhenSomeSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        assertTrue(testSubject.supportsReset());
    }

    @Test
    void testSupportResetWhenNoneSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(false);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        assertFalse(testSubject.supportsReset());
    }

    @Test
    void testPerformReset() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        testSubject.performReset();

        verify(mockedEventHandlerInvokerOne, times(1)).performReset(eq(NO_RESET_PAYLOAD));
        verify(mockedEventHandlerInvokerTwo, never()).performReset(eq(NO_RESET_PAYLOAD));
    }

    @Test
    void testPerformResetWithResetContext() {
        String resetContext = "reset-context";

        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        testSubject.performReset(resetContext);

        verify(mockedEventHandlerInvokerOne, times(1)).performReset(eq(resetContext));
        verify(mockedEventHandlerInvokerTwo, never()).performReset(eq(resetContext));
    }

    @Test
    void testInvokersNotSupportingResetDoNotReceiveRedeliveries() throws Exception {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        assertTrue(testSubject.canHandle(testEventMessage, testSegment));
        testSubject.handle(testEventMessage, testSegment);
        testSubject.handle(replayMessage, testSegment);

        InOrder inOrder = inOrder(mockedEventHandlerInvokerOne, mockedEventHandlerInvokerTwo);
        inOrder.verify(mockedEventHandlerInvokerOne).handle(testEventMessage, testSegment);
        inOrder.verify(mockedEventHandlerInvokerTwo).handle(testEventMessage, testSegment);
        inOrder.verify(mockedEventHandlerInvokerOne).handle(replayMessage, testSegment);

        verify(mockedEventHandlerInvokerTwo, never()).handle(eq(replayMessage), any());
    }

    @Test
    void testPerformResetThrowsException() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);
        doThrow(RuntimeException.class).when(mockedEventHandlerInvokerOne).performReset(any());

        assertThrows(Exception.class, testSubject::performReset);
    }
}

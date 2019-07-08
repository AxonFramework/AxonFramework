/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class MultiEventHandlerInvokerTest {

    private MultiEventHandlerInvoker testSubject;

    private EventHandlerInvoker mockedEventHandlerInvokerOne = mock(EventHandlerInvoker.class);
    private EventHandlerInvoker mockedEventHandlerInvokerTwo = mock(EventHandlerInvoker.class);

    private EventMessage<String> testEventMessage;
    private EventMessage<String> replayMessage;
    private Segment testSegment;

    @Before
    public void setUp() {
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
    public void testDelegatesReturnsSetDelegates() {
        List<EventHandlerInvoker> result = testSubject.delegates();

        assertTrue(result.contains(mockedEventHandlerInvokerOne));
        assertTrue(result.contains(mockedEventHandlerInvokerTwo));
    }

    @Test
    public void testCanHandleCallsCanHandleOnTheFirstDelegateToReturn() {
        testSubject.canHandle(testEventMessage, testSegment);

        verify(mockedEventHandlerInvokerOne).canHandle(testEventMessage, testSegment);
        verifyZeroInteractions(mockedEventHandlerInvokerTwo);
    }

    @Test
    public void testHandleCallsCanHandleAndHandleOfAllDelegates() throws Exception {
        testSubject.handle(testEventMessage, testSegment);

        verify(mockedEventHandlerInvokerOne).canHandle(testEventMessage, testSegment);
        verify(mockedEventHandlerInvokerOne).handle(testEventMessage, testSegment);
        verify(mockedEventHandlerInvokerTwo).canHandle(testEventMessage, testSegment);
        verify(mockedEventHandlerInvokerTwo).handle(testEventMessage, testSegment);
    }

    @Test(expected = RuntimeException.class)
    public void testHandleThrowsExceptionIfDelegatesThrowAnException() throws Exception {
        doThrow(new RuntimeException()).when(mockedEventHandlerInvokerTwo).handle(testEventMessage, testSegment);

        testSubject.handle(testEventMessage, testSegment);
    }

    @Test
    public void testSupportResetWhenAllSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(true);

        assertTrue(testSubject.supportsReset());
    }

    @Test
    public void testSupportResetWhenSomeSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        assertTrue(testSubject.supportsReset());
    }

    @Test
    public void testSupportResetWhenNoneSupport() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(false);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        assertFalse(testSubject.supportsReset());
    }

    @Test
    public void testPerformReset() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);

        testSubject.performReset();

        verify(mockedEventHandlerInvokerOne, times(1)).performReset();
        verify(mockedEventHandlerInvokerTwo, never()).performReset();
    }

    @Test
    public void testInvokersNotSupportingResetDoNotReceiveRedeliveries() throws Exception {
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

    @Test(expected = Exception.class)
    public void testPerformResetThrowsException() {
        when(mockedEventHandlerInvokerOne.supportsReset()).thenReturn(true);
        when(mockedEventHandlerInvokerTwo.supportsReset()).thenReturn(false);
        doThrow().when(mockedEventHandlerInvokerOne).performReset();

        testSubject.performReset();
    }
}

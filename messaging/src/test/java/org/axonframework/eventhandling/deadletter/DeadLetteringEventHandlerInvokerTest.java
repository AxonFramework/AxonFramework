/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.messaging.deadletter.DeadLetterEntry;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DeadLetteringEventHandlerInvoker}.
 *
 * @author Steven van Beelen
 */
class DeadLetteringEventHandlerInvokerTest {

    private static final String TEST_PROCESSING_GROUP = "some-processing-group";
    private static final String TEST_SEQUENCE_ID = "my-sequence";
    private static final EventHandlingQueueIdentifier TEST_QUEUE_ID =
            new EventHandlingQueueIdentifier(TEST_SEQUENCE_ID, TEST_PROCESSING_GROUP);
    private static final EventMessage<Object> TEST_EVENT = GenericEventMessage.asEventMessage("some-payload");

    private EventHandlerInvoker delegate;
    private DeadLetterQueue<EventMessage<?>> queue;

    private DeadLetteringEventHandlerInvoker testSubject;

    @BeforeEach
    void setUp() {
        delegate = mock(EventHandlerInvoker.class);
        //noinspection unchecked
        queue = mock(DeadLetterQueue.class);

        testSubject = DeadLetteringEventHandlerInvoker.builder()
                                                      .delegate(delegate)
                                                      .queue(queue)
                                                      .processingGroup(TEST_PROCESSING_GROUP)
                                                      .build();
    }

    @Test
    void testHandleHandlesEventJustFine() throws Exception {
        when(delegate.sequenceIdentifier(TEST_EVENT)).thenReturn(TEST_SEQUENCE_ID);
        when(queue.enqueueIfPresent(any(), any())).thenReturn(Optional.empty());

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(delegate).handle(TEST_EVENT, Segment.ROOT_SEGMENT);
        verify(queue, never()).enqueue(any(), eq(TEST_EVENT), any());
    }

    @Test
    void testHandleEnqueuesWhenDelegateThrowsAnException() throws Exception {
        RuntimeException testCause = new RuntimeException("some-cause");

        when(delegate.sequenceIdentifier(TEST_EVENT)).thenReturn(TEST_SEQUENCE_ID);
        doThrow(testCause).when(delegate).handle(TEST_EVENT, Segment.ROOT_SEGMENT);
        when(queue.enqueueIfPresent(any(), any())).thenReturn(Optional.empty());

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(delegate).handle(TEST_EVENT, Segment.ROOT_SEGMENT);
        verify(queue).enqueue(TEST_QUEUE_ID, TEST_EVENT, testCause);
    }

    @Test
    void testHandleDoesNotHandleEventOnDelegateWhenEnqueueIfPresentReturnsTrue() throws Exception {
        when(delegate.sequenceIdentifier(TEST_EVENT)).thenReturn(TEST_SEQUENCE_ID);
        //noinspection unchecked
        when(queue.enqueueIfPresent(any(), any())).thenReturn(Optional.of(mock(DeadLetterEntry.class)));

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(delegate, never()).handle(any(), any());
        verify(queue, never()).enqueue(any(), eq(TEST_EVENT), any());
    }

    @Test
    void testCanHandleIsDelegated() {
        when(delegate.canHandle(TEST_EVENT, Segment.ROOT_SEGMENT)).thenReturn(true);

        boolean result = testSubject.canHandle(TEST_EVENT, Segment.ROOT_SEGMENT);

        assertTrue(result);
        verify(delegate).canHandle(TEST_EVENT, Segment.ROOT_SEGMENT);
    }

    @Test
    void testCanHandleTypeIsDelegated() {
        when(delegate.canHandleType(String.class)).thenReturn(false);

        boolean result = testSubject.canHandleType(String.class);

        assertFalse(result);
        verify(delegate).canHandleType(String.class);
    }

    @Test
    void testSupportsResetIsDelegated() {
        when(delegate.supportsReset()).thenReturn(true);

        boolean result = testSubject.supportsReset();

        assertTrue(result);
        verify(delegate).supportsReset();
    }

    @Test
    void testPerformResetIsDelegated() {
        testSubject.performReset();

        verify(delegate).performReset();
    }

    @Test
    void testPerformResetWithContextIsDelegated() {
        String testContext = "some-reset-context";

        testSubject.performReset(testContext);

        verify(delegate).performReset(testContext);
    }

    @Test
    void testSequenceIdentifierIsDelegated() {
        when(delegate.sequenceIdentifier(TEST_EVENT)).thenReturn(TEST_EVENT.getIdentifier());

        Object result = testSubject.sequenceIdentifier(TEST_EVENT);

        assertEquals(TEST_EVENT.getIdentifier(), result);
        verify(delegate).sequenceIdentifier(TEST_EVENT);
    }

    @Test
    void testBuildWithNullDelegateThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.delegate(null));
    }

    @Test
    void testBuildWithoutDelegateThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder()
                                                .queue(queue)
                                                .processingGroup(TEST_PROCESSING_GROUP);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void testBuildWithNullDeadLetterQueueThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.queue(null));
    }

    @Test
    void testBuildWithoutDeadLetterQueueThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder()
                                                .delegate(delegate)
                                                .processingGroup(TEST_PROCESSING_GROUP);
        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void testBuildWithNullProcessingGroupThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.processingGroup(null));
    }

    @Test
    void testBuildWithEmptyProcessingGroupThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.processingGroup(""));
    }

    @Test
    void testBuildWithoutProcessingGroupThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder()
                                                .delegate(delegate)
                                                .queue(queue);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void testBuildWithNullExecutorThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.executorService(null));
    }
}
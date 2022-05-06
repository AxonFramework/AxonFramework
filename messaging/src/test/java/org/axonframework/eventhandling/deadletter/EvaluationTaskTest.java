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

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.messaging.deadletter.DeadLetterEntry;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EvaluationTask}.
 *
 * @author Steven van Beelen
 */
class EvaluationTaskTest {

    private static final String TEST_PROCESSING_GROUP = "some-processing-group";
    @SuppressWarnings("rawtypes") // The DeadLetterEntry mocks don't like the generic, at all...
    private static final EventMessage TEST_EVENT =
            GenericEventMessage.asEventMessage("Then this happened..." + UUID.randomUUID());

    private EventMessageHandler eventHandlerOne;
    private EventMessageHandler eventHandlerTwo;
    private DeadLetterQueue<EventMessage<?>> queue;
    private TransactionManager transactionManager;
    private ListenerInvocationErrorHandler listenerInvocationErrorHandler;

    private EvaluationTask testSubject;

    @BeforeEach
    void setUp() {
        eventHandlerOne = mock(EventMessageHandler.class);
        eventHandlerTwo = mock(EventMessageHandler.class);
        List<EventMessageHandler> eventHandlingComponents = new ArrayList<>();
        eventHandlingComponents.add(eventHandlerOne);
        eventHandlingComponents.add(eventHandlerTwo);
        //noinspection unchecked
        queue = mock(DeadLetterQueue.class);
        transactionManager = spy(new StubTransactionManager());
        listenerInvocationErrorHandler = spy(new LoggingErrorHandler());

        testSubject = new EvaluationTask(
                eventHandlingComponents,
                queue,
                TEST_PROCESSING_GROUP,
                transactionManager,
                listenerInvocationErrorHandler
        );
    }

    @Test
    void testRunEndsWhenThereIsNoDeadLetterAvailable() {
        when(queue.take(TEST_PROCESSING_GROUP)).thenReturn(Optional.empty());

        testSubject.run();

        verify(transactionManager).fetchInTransaction(any());
        verifyNoInteractions(eventHandlerOne);
        verifyNoInteractions(eventHandlerTwo);
        verifyNoInteractions(listenerInvocationErrorHandler);
    }

    @Test
    void testRunEvaluatesDeadLetterSuccessfully() throws Exception {
        //noinspection unchecked
        DeadLetterEntry<EventMessage<?>> testDeadLetter = mock(DeadLetterEntry.class);
        //noinspection unchecked
        when(testDeadLetter.message()).thenReturn(TEST_EVENT);
        when(queue.take(TEST_PROCESSING_GROUP)).thenReturn(Optional.of(testDeadLetter))
                                               .thenReturn(Optional.empty());

        testSubject.run();

        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verifyNoInteractions(listenerInvocationErrorHandler);
        verify(testDeadLetter).acknowledge();
        verify(testDeadLetter, never()).requeue();
    }

    @Test
    void testRunEvaluatesDeadLetterSuccessfullyAndFailsOnAcknowledge() throws Exception {
        //noinspection unchecked
        DeadLetterEntry<EventMessage<?>> testDeadLetter = mock(DeadLetterEntry.class);
        //noinspection unchecked
        when(testDeadLetter.message()).thenReturn(TEST_EVENT);
        doThrow(new RuntimeException()).when(testDeadLetter).acknowledge();
        when(queue.take(TEST_PROCESSING_GROUP)).thenReturn(Optional.of(testDeadLetter))
                                               .thenReturn(Optional.empty());

        testSubject.run();

        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verifyNoInteractions(listenerInvocationErrorHandler);
        verify(testDeadLetter).acknowledge();
        verify(testDeadLetter).requeue();
    }

    // Note that this depends on the ListenerInvocationErrorHandler.
    // If this error handler swallows the exception, the dead-letter is "successfully" evaluated.
    @Test
    void testRunEvaluatesDeadLetterUnsuccessfully() throws Exception {
        //noinspection unchecked
        DeadLetterEntry<EventMessage<?>> testDeadLetter = mock(DeadLetterEntry.class);
        Exception testException = new RuntimeException();

        //noinspection unchecked
        when(testDeadLetter.message()).thenReturn(TEST_EVENT);
        when(queue.take(TEST_PROCESSING_GROUP)).thenReturn(Optional.of(testDeadLetter));

        when(eventHandlerTwo.handle(TEST_EVENT)).thenThrow(testException);

        testSubject.run();

        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verify(listenerInvocationErrorHandler).onError(testException, TEST_EVENT, eventHandlerTwo);
        verify(testDeadLetter, never()).acknowledge();
        verify(testDeadLetter).requeue();
    }

    // Note that this depends on the ListenerInvocationErrorHandler.
    // If this error handler swallows the exception, the dead-letter is "successfully" evaluated.
    @Test
    void testRunEvaluatesDeadLetterUnsuccessfullyAndFailsOnRequeue() throws Exception {
        //noinspection unchecked
        DeadLetterEntry<EventMessage<?>> testDeadLetter = mock(DeadLetterEntry.class);
        Exception testException = new RuntimeException();

        //noinspection unchecked
        when(testDeadLetter.message()).thenReturn(TEST_EVENT);
        doThrow(new RuntimeException()).when(testDeadLetter).requeue();
        when(queue.take(TEST_PROCESSING_GROUP)).thenReturn(Optional.of(testDeadLetter));

        when(eventHandlerTwo.handle(TEST_EVENT)).thenThrow(testException);

        testSubject.run();

        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verify(listenerInvocationErrorHandler).onError(testException, TEST_EVENT, eventHandlerTwo);
        verify(testDeadLetter, never()).acknowledge();
        verify(testDeadLetter).requeue();
    }

    // This stub TransactionManager is used for spying.
    private static class StubTransactionManager implements TransactionManager {

        @Override
        public Transaction startTransaction() {
            return NoTransactionManager.INSTANCE.startTransaction();
        }
    }
}
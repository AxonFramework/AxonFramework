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
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.DoNotEnqueue;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DeadLetteredEventProcessingTask}.
 *
 * @author Steven van Beelen
 */
class DeadLetteredEventProcessingTaskTest {

    private static final String TEST_PROCESSING_GROUP = "some-processing-group";
    private static final EventSequenceIdentifier TEST_ID =
            new EventSequenceIdentifier("sequenceId", TEST_PROCESSING_GROUP);
    @SuppressWarnings("rawtypes") // The DeadLetter mocks don't like the generic, at all...
    private static final EventMessage TEST_EVENT =
            GenericEventMessage.asEventMessage("Then this happened..." + UUID.randomUUID());
    private static final EnqueueDecision<DeadLetter<EventMessage<?>>> TEST_DECISION = Decisions.ignore();

    private EventMessageHandler eventHandlerOne;
    private EventMessageHandler eventHandlerTwo;
    private EnqueuePolicy<DeadLetter<EventMessage<?>>> enqueuePolicy;
    private TransactionManager transactionManager;
    private ListenerInvocationErrorHandler listenerInvocationErrorHandler;

    private DeadLetteredEventProcessingTask testSubject;

    @BeforeEach
    void setUp() {
        eventHandlerOne = mock(EventMessageHandler.class);
        eventHandlerTwo = mock(EventMessageHandler.class);
        List<EventMessageHandler> eventHandlingComponents = new ArrayList<>();
        eventHandlingComponents.add(eventHandlerOne);
        eventHandlingComponents.add(eventHandlerTwo);
        //noinspection unchecked
        enqueuePolicy = mock(EnqueuePolicy.class);
        when(enqueuePolicy.decide(any(), any())).thenReturn(TEST_DECISION);
        transactionManager = spy(new StubTransactionManager());
        listenerInvocationErrorHandler = spy(new StubPropagatingErrorHandler());

        testSubject = new DeadLetteredEventProcessingTask(
                eventHandlingComponents, enqueuePolicy, transactionManager, listenerInvocationErrorHandler
        );
    }

    @Test
    void testProcessLetterSuccessfully() throws Exception {
        //noinspection unchecked
        DeadLetter<EventMessage<?>> testLetter = mock(DeadLetter.class);
        when(testLetter.sequenceIdentifier()).thenReturn(TEST_ID);
        //noinspection unchecked
        when(testLetter.message()).thenReturn(TEST_EVENT);

        EnqueueDecision<DeadLetter<EventMessage<?>>> result = testSubject.process(testLetter);

        assertEquals(DoNotEnqueue.class, result.getClass());
        verify(transactionManager).startTransaction();
        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verifyNoInteractions(listenerInvocationErrorHandler);
        verifyNoInteractions(enqueuePolicy);
    }

    // Note that this depends on the ListenerInvocationErrorHandler.
    // If this error handler swallows the exception, the dead-letter is not rolled back.
    @Test
    void testProcessLetterUnsuccessfully() throws Exception {
        //noinspection unchecked
        DeadLetter<EventMessage<?>> testLetter = mock(DeadLetter.class);
        when(testLetter.sequenceIdentifier()).thenReturn(TEST_ID);
        //noinspection unchecked
        when(testLetter.message()).thenReturn(TEST_EVENT);
        Exception testException = new RuntimeException();

        when(eventHandlerTwo.handle(TEST_EVENT)).thenThrow(testException);

        EnqueueDecision<DeadLetter<EventMessage<?>>> result = testSubject.process(testLetter);

        assertEquals(TEST_DECISION, result);
        verify(transactionManager).startTransaction();
        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verify(listenerInvocationErrorHandler).onError(testException, TEST_EVENT, eventHandlerTwo);
        verify(enqueuePolicy).decide(testLetter, testException);
    }

    // This stub TransactionManager is used for spying.
    private static class StubTransactionManager implements TransactionManager {

        @Override
        public Transaction startTransaction() {
            return NoTransactionManager.INSTANCE.startTransaction();
        }
    }

    // This stub ListenerInvocationErrorHandler is used for spying.
    private static class StubPropagatingErrorHandler implements ListenerInvocationErrorHandler {

        @Override
        public void onError(@Nonnull Exception exception,
                            @Nonnull EventMessage<?> event,
                            @Nonnull EventMessageHandler eventHandler) throws Exception {
            throw exception;
        }
    }
}
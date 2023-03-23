/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.DoNotEnqueue;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DeadLetteredEventProcessingTask}.
 *
 * @author Steven van Beelen
 */
class DeadLetteredEventProcessingTaskTest {

    @SuppressWarnings("rawtypes") // The DeadLetter mocks don't like the generic, at all...
    private static final EventMessage TEST_EVENT =
            GenericEventMessage.asEventMessage("Then this happened..." + UUID.randomUUID());
    private static final EnqueueDecision<EventMessage<?>> TEST_DECISION = Decisions.ignore();

    private EventMessageHandler eventHandlerOne;
    private EventMessageHandler eventHandlerTwo;
    private EnqueuePolicy<EventMessage<?>> enqueuePolicy;
    private TransactionManager transactionManager;

    private List<EventMessageHandler> eventHandlingComponents;

    private DeadLetteredEventProcessingTask testSubject;

    @BeforeEach
    void setUp() {
        eventHandlerOne = mock(EventMessageHandler.class);
        eventHandlerTwo = mock(EventMessageHandler.class);
        eventHandlingComponents = new ArrayList<>();
        eventHandlingComponents.add(eventHandlerOne);
        eventHandlingComponents.add(eventHandlerTwo);
        //noinspection unchecked
        enqueuePolicy = mock(EnqueuePolicy.class);
        when(enqueuePolicy.decide(any(), any())).thenReturn(TEST_DECISION);
        transactionManager = spy(new StubTransactionManager());

        testSubject = new DeadLetteredEventProcessingTask(eventHandlingComponents,
                                                          Collections.emptyList(),
                                                          enqueuePolicy,
                                                          transactionManager);
    }

    @Test
    void taskProcessesLetterSuccessfully() throws Exception {
        //noinspection unchecked
        DeadLetter<EventMessage<?>> testLetter = mock(DeadLetter.class);
        //noinspection unchecked
        when(testLetter.message()).thenReturn(TEST_EVENT);

        EnqueueDecision<EventMessage<?>> result = testSubject.process(testLetter);

        assertEquals(DoNotEnqueue.class, result.getClass());
        verify(transactionManager).startTransaction();
        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verifyNoInteractions(enqueuePolicy);
    }

    @Test
    void taskProcessesLetterUnsuccessfullyWhenHandlersThrowsAnException() throws Exception {
        //noinspection unchecked
        DeadLetter<EventMessage<?>> testLetter = mock(DeadLetter.class);
        //noinspection unchecked
        when(testLetter.message()).thenReturn(TEST_EVENT);
        Exception testException = new RuntimeException();

        when(eventHandlerTwo.handle(TEST_EVENT)).thenThrow(testException);

        EnqueueDecision<EventMessage<?>> result = testSubject.process(testLetter);

        assertEquals(TEST_DECISION, result);
        verify(transactionManager).startTransaction();
        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verify(enqueuePolicy).decide(testLetter, testException);
    }

    @Test
    void useInterceptorToHandleError() throws Exception {
        testSubject = new DeadLetteredEventProcessingTask(eventHandlingComponents,
                                                          Collections.singletonList(errorCatchingInterceptor()),
                                                          enqueuePolicy,
                                                          transactionManager);
        //noinspection unchecked
        DeadLetter<EventMessage<?>> testLetter = mock(DeadLetter.class);
        //noinspection unchecked
        when(testLetter.message()).thenReturn(TEST_EVENT);
        Exception testException = new RuntimeException();

        when(eventHandlerTwo.handle(TEST_EVENT)).thenThrow(testException);

        EnqueueDecision<EventMessage<?>> result = testSubject.process(testLetter);

        assertFalse(result.shouldEnqueue());
        verify(transactionManager).startTransaction();
        verify(eventHandlerOne).handle(TEST_EVENT);
        verify(eventHandlerTwo).handle(TEST_EVENT);
        verify(enqueuePolicy, never()).decide(testLetter, testException);
    }

    // This stub TransactionManager is used for spying.
    private static class StubTransactionManager implements TransactionManager {

        @Override
        public Transaction startTransaction() {
            return NoTransactionManager.INSTANCE.startTransaction();
        }
    }

    private MessageHandlerInterceptor<? super EventMessage<?>> errorCatchingInterceptor() {
        return (event, chain) -> {
            try {
                chain.proceed();
            } catch (RuntimeException e) {
                return event;
            }
            return event;
        };
    }
}
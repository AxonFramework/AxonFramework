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
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.messaging.deadletter.DeadLetterEntry;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.axonframework.messaging.deadletter.QueueIdentifier;
import org.axonframework.utils.EventTestUtils;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DeadLetteringEventHandlerInvoker}.
 *
 * @author Steven van Beelen
 */
class DeadLetteringEventHandlerInvokerTest {

    private static final String TEST_PROCESSING_GROUP = "some-processing-group";
    private static final DomainEventMessage<String> TEST_EVENT = EventTestUtils.createEvent();
    private static final QueueIdentifier TEST_QUEUE_ID =
            new EventHandlingQueueIdentifier(TEST_EVENT.getAggregateIdentifier(), TEST_PROCESSING_GROUP);

    private EventMessageHandler handler;
    private SequencingPolicy<? super EventMessage<?>> sequencingPolicy;
    private DeadLetterQueue<EventMessage<?>> queue;
    private TransactionManager transactionManager;

    private DeadLetteringEventHandlerInvoker testSubject;

    @BeforeEach
    void setUp() {
        handler = mock(EventMessageHandler.class);
        sequencingPolicy = spy(SequentialPerAggregatePolicy.instance());
        //noinspection unchecked
        queue = mock(DeadLetterQueue.class);
        transactionManager = spy(new StubTransactionManager());

        setTestSubject(createTestSubject());
    }

    private void setTestSubject(DeadLetteringEventHandlerInvoker testSubject) {
        this.testSubject = testSubject;
    }

    private DeadLetteringEventHandlerInvoker createTestSubject() {
        return createTestSubject(builder -> builder);
    }

    private DeadLetteringEventHandlerInvoker createTestSubject(
            UnaryOperator<DeadLetteringEventHandlerInvoker.Builder> customization
    ) {
        DeadLetteringEventHandlerInvoker.Builder invokerBuilder =
                DeadLetteringEventHandlerInvoker.builder()
                                                .eventHandlers(handler)
                                                .sequencingPolicy(sequencingPolicy)
                                                .listenerInvocationErrorHandler(PropagatingErrorHandler.instance())
                                                .queue(queue)
                                                .processingGroup(TEST_PROCESSING_GROUP)
                                                .transactionManager(transactionManager);
        return customization.apply(invokerBuilder).build();
    }

    @Test
    void testHandleHandlesEventJustFine() throws Exception {
        when(queue.enqueueIfPresent(any(), any())).thenReturn(Optional.empty());

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(TEST_EVENT);
        verify(handler).handle(TEST_EVENT);
        verify(queue).enqueueIfPresent(eq(TEST_QUEUE_ID), eq(TEST_EVENT));
        verify(queue, never()).enqueue(any(), eq(TEST_EVENT), any());
        verify(transactionManager).fetchInTransaction(any());
        verify(transactionManager, never()).executeInTransaction(any());
    }

    @Test
    void testHandleIgnoresEventForNonMatchingSegment() throws Exception {
        Segment testSegment = mock(Segment.class);
        when(testSegment.matches(any())).thenReturn(false);

        testSubject.handle(TEST_EVENT, testSegment);

        verify(sequencingPolicy).getSequenceIdentifierFor(TEST_EVENT);
        verifyNoInteractions(handler);
        verifyNoInteractions(queue);
        verifyNoInteractions(transactionManager);
    }

    @Test
    void testHandleEnqueuesWhenDelegateThrowsAnException() throws Exception {
        RuntimeException testCause = new RuntimeException("some-cause");

        doThrow(testCause).when(handler).handle(TEST_EVENT);
        when(queue.enqueueIfPresent(any(), any())).thenReturn(Optional.empty());

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(TEST_EVENT);
        verify(handler).handle(TEST_EVENT);
        verify(queue).enqueueIfPresent(TEST_QUEUE_ID, TEST_EVENT);
        verify(queue).enqueue(TEST_QUEUE_ID, TEST_EVENT, testCause);
        verify(transactionManager).fetchInTransaction(any());
        verify(transactionManager).executeInTransaction(any());
    }

    @Test
    void testHandleDoesNotHandleEventOnDelegateWhenEnqueueIfPresentReturnsTrue() throws Exception {
        //noinspection unchecked
        when(queue.enqueueIfPresent(any(), any())).thenReturn(Optional.of(mock(DeadLetterEntry.class)));

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(TEST_EVENT);
        verify(handler, never()).handle(TEST_EVENT);
        verify(queue, never()).enqueue(any(), eq(TEST_EVENT), any());
        verify(transactionManager).fetchInTransaction(any());
        verify(transactionManager, never()).executeInTransaction(any());
    }

    @Test
    void testPerformResetOnlyInvokesParentForAllowResetSetToFalse() {
        setTestSubject(createTestSubject(builder -> builder.allowReset(false)));

        testSubject.performReset();

        verifyNoInteractions(queue);
        verifyNoInteractions(transactionManager);
        verify(handler).prepareReset(null);
    }

    @Test
    void testPerformResetClearsOutTheQueueForAllowResetSetToTrue() {
        setTestSubject(createTestSubject(builder -> builder.allowReset(true)));

        testSubject.performReset();

        verify(queue).clear(TEST_PROCESSING_GROUP);
        verify(transactionManager).executeInTransaction(any());
        verify(handler).prepareReset(null);
    }

    @Test
    void testPerformResetWithContextOnlyInvokesParentForAllowResetSetToFalse() {
        setTestSubject(createTestSubject(builder -> builder.allowReset(false)));

        String testContext = "some-reset-context";

        testSubject.performReset(testContext);

        verifyNoInteractions(queue);
        verifyNoInteractions(transactionManager);
        verify(handler).prepareReset(testContext);
    }

    @Test
    void testPerformResetWithContextClearsOutTheQueueForAllowResetSetToTrue() {
        setTestSubject(createTestSubject(builder -> builder.allowReset(true)));

        String testContext = "some-reset-context";

        testSubject.performReset(testContext);

        verify(queue).clear(TEST_PROCESSING_GROUP);
        verify(transactionManager).executeInTransaction(any());
        verify(handler).prepareReset(testContext);
    }

    @Test
    void testRegisterLifecycleHandlersRegistersInvokersStart() {
        DeadLetteringEventHandlerInvoker spiedTestSubject = spy(createTestSubject());
        AtomicInteger onStartInvoked = new AtomicInteger(0);
        AtomicInteger onShutdownInvoked = new AtomicInteger(0);

        spiedTestSubject.registerLifecycleHandlers(new Lifecycle.LifecycleRegistry() {
            @Override
            public void onStart(int phase, Lifecycle.LifecycleHandler action) {
                onStartInvoked.incrementAndGet();
                action.run();
            }

            @Override
            public void onShutdown(int phase, Lifecycle.LifecycleHandler action) {
                onShutdownInvoked.incrementAndGet();
            }
        });

        assertEquals(1, onStartInvoked.get());
        assertEquals(0, onShutdownInvoked.get());
        verify(spiedTestSubject).start();
    }

    @Test
    void testStartRegistersOnAvailableRunnableAndReleasesDeadLetters() {
        testSubject.start();

        verify(queue).onAvailable(eq(TEST_PROCESSING_GROUP), any());
        verify(queue).release(any());
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
                                                .processingGroup(TEST_PROCESSING_GROUP)
                                                .transactionManager(NoTransactionManager.instance());

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
                                                .queue(queue)
                                                .transactionManager(NoTransactionManager.instance());

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void testBuildWithNullTransactionManagerThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.transactionManager(null));
    }

    @Test
    void testBuildWithoutTransactionManagerThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder()
                                                .queue(queue)
                                                .processingGroup(TEST_PROCESSING_GROUP);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void testBuildWithNullListenerInvocationErrorHandlerThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.listenerInvocationErrorHandler(null));
    }

    // This stub TransactionManager is used for spying.
    private static class StubTransactionManager implements TransactionManager {

        @Override
        public Transaction startTransaction() {
            return NoTransactionManager.INSTANCE.startTransaction();
        }
    }
}
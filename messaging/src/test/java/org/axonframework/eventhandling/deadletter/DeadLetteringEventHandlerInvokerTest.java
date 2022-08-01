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
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequenceIdentifier;
import org.axonframework.utils.EventTestUtils;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Function;
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
    private static final SequenceIdentifier TEST_QUEUE_ID =
            new EventSequencedIdentifier(TEST_EVENT.getAggregateIdentifier(), TEST_PROCESSING_GROUP);
    private static final DeadLetter<EventMessage<?>> TEST_DEAD_LETTER =
            new GenericDeadLetter<>(TEST_QUEUE_ID, TEST_EVENT);

    private EventMessageHandler handler;
    private SequencingPolicy<? super EventMessage<?>> sequencingPolicy;
    private SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>> queue;
    private EnqueuePolicy<DeadLetter<EventMessage<?>>> enqueuePolicy;
    private TransactionManager transactionManager;

    private DeadLetteringEventHandlerInvoker testSubject;

    @BeforeEach
    void setUp() {
        handler = mock(EventMessageHandler.class);
        sequencingPolicy = spy(SequentialPerAggregatePolicy.instance());
        //noinspection unchecked
        queue = mock(SequencedDeadLetterQueue.class);
        //noinspection unchecked
        enqueuePolicy = mock(EnqueuePolicy.class);
        when(enqueuePolicy.decide(any(), any())).thenReturn(Decisions.ignore());
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
                                                .enqueuePolicy(enqueuePolicy)
                                                .processingGroup(TEST_PROCESSING_GROUP)
                                                .transactionManager(transactionManager);
        return customization.apply(invokerBuilder).build();
    }

    @Test
    void testHandleHandlesEventJustFine() throws Exception {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        DeadLetter<EventMessage<?>> expectedIfPresentLetter = new GenericDeadLetter<>(TEST_QUEUE_ID, TEST_EVENT);

        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(TEST_EVENT);
        verify(handler).handle(TEST_EVENT);

        verify(transactionManager).fetchInTransaction(any());
        //noinspection unchecked
        ArgumentCaptor<Function<SequenceIdentifier, DeadLetter<EventMessage<?>>>> enqueueIfPresentCaptor =
                ArgumentCaptor.forClass(Function.class);
        verify(queue).enqueueIfPresent(eq(TEST_QUEUE_ID), enqueueIfPresentCaptor.capture());
        assertLetter(expectedIfPresentLetter, enqueueIfPresentCaptor.getValue().apply(TEST_QUEUE_ID));

        verify(queue, never()).enqueue(any());
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
    void testHandleEnqueuesOnShouldEnqueueDecisionWhenDelegateThrowsAnException() throws Exception {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        RuntimeException testCause = new RuntimeException("some-cause");
        DeadLetter<EventMessage<?>> expectedIfPresentLetter = new GenericDeadLetter<>(TEST_QUEUE_ID, TEST_EVENT);
        DeadLetter<EventMessage<?>> expectedEnqueuedLetter =
                new GenericDeadLetter<>(TEST_QUEUE_ID, TEST_EVENT, testCause);

        doThrow(testCause).when(handler).handle(TEST_EVENT);
        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(TEST_EVENT);
        verify(handler).handle(TEST_EVENT);

        verify(transactionManager).fetchInTransaction(any());
        //noinspection unchecked
        ArgumentCaptor<Function<SequenceIdentifier, DeadLetter<EventMessage<?>>>> enqueueIfPresentCaptor =
                ArgumentCaptor.forClass(Function.class);
        verify(queue).enqueueIfPresent(eq(TEST_QUEUE_ID), enqueueIfPresentCaptor.capture());
        assertLetter(expectedIfPresentLetter, enqueueIfPresentCaptor.getValue().apply(TEST_QUEUE_ID));

        //noinspection unchecked
        ArgumentCaptor<DeadLetter<EventMessage<?>>> policyCaptor = ArgumentCaptor.forClass(DeadLetter.class);
        verify(enqueuePolicy).decide(policyCaptor.capture(), eq(testCause));
        assertLetter(expectedEnqueuedLetter, policyCaptor.getValue());

        //noinspection unchecked
        ArgumentCaptor<DeadLetter<EventMessage<?>>> enqueueCaptor = ArgumentCaptor.forClass(DeadLetter.class);
        verify(queue).enqueue(enqueueCaptor.capture());
        assertLetter(expectedEnqueuedLetter, enqueueCaptor.getValue());
        verify(transactionManager).executeInTransaction(any());
    }

    @Test
    void testHandleDoesNotEnqueueForShouldNotEnqueueDecisionWhenDelegateThrowsAnException() throws Exception {
        when(enqueuePolicy.decide(any(), any())).thenReturn(Decisions.doNotEnqueue());

        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        RuntimeException testCause = new RuntimeException("some-cause");
        DeadLetter<EventMessage<?>> expectedIfPresentLetter =
                new GenericDeadLetter<>(TEST_QUEUE_ID, TEST_EVENT);
        DeadLetter<EventMessage<?>> expectedEnqueuedLetter =
                new GenericDeadLetter<>(TEST_QUEUE_ID, TEST_EVENT, testCause);

        doThrow(testCause).when(handler).handle(TEST_EVENT);
        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(TEST_EVENT);
        verify(handler).handle(TEST_EVENT);

        verify(transactionManager).fetchInTransaction(any());
        //noinspection unchecked
        ArgumentCaptor<Function<SequenceIdentifier, DeadLetter<EventMessage<?>>>> enqueueIfPresentCaptor =
                ArgumentCaptor.forClass(Function.class);
        verify(queue).enqueueIfPresent(eq(TEST_QUEUE_ID), enqueueIfPresentCaptor.capture());
        assertLetter(expectedIfPresentLetter, enqueueIfPresentCaptor.getValue().apply(TEST_QUEUE_ID));

        //noinspection unchecked
        ArgumentCaptor<DeadLetter<EventMessage<?>>> policyCaptor = ArgumentCaptor.forClass(DeadLetter.class);
        verify(enqueuePolicy).decide(policyCaptor.capture(), eq(testCause));
        assertLetter(expectedEnqueuedLetter, policyCaptor.getValue());

        verify(queue, never()).enqueue(any());
        verify(transactionManager, never()).executeInTransaction(any());
    }

    @Test
    void testHandleDoesNotHandleEventOnDelegateWhenEnqueueIfPresentReturnsTrue() throws Exception {
        when(queue.enqueueIfPresent(any(), any())).thenReturn(true);

        testSubject.handle(TEST_EVENT, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(TEST_EVENT);
        verify(handler, never()).handle(TEST_EVENT);
        verify(queue, never()).enqueue(TEST_DEAD_LETTER);
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
    void testRetry() {
        when(queue.process(eq(TEST_PROCESSING_GROUP), any())).thenReturn(true);

        boolean result = testSubject.retry();

        assertTrue(result);

        verify(transactionManager).startTransaction();
        verify(queue).process(eq(TEST_PROCESSING_GROUP), any());
    }

    /*
        @Test
    void testReleaseReturnsTrueAndEvictsTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        I testId = generateQueueId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);

        boolean releaseResult = testSubject.release(testTask);
        assertTrue(releaseResult);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetters(testId).iterator();
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testReleaseReturnsTrueAndRequeuesTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        I testId = generateQueueId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);

        Instant expectedLastTouched = setAndGetTime();
        D expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean releaseResult = testSubject.release(testTask);
        assertTrue(releaseResult);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetters(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

     * A "claimed sequence" in this case means that a letter with {@link QueueIdentifier} {@code x} is not
     * {@link SequencedDeadLetterQueue#evict(DeadLetter)  evicted} or
     * {@link SequencedDeadLetterQueue#requeue(DeadLetter, Throwable) requeued} yet. Furthermore, if it's the sole letter,
     * nothing should be returned. This approach ensure the events for a given {@link QueueIdentifier} are handled in
     * the order they've been dead-lettered (a.k.a., in sequence).
     * <p>
     * TODO: 27-07-22 sadly enough, this is flaky...sometimes the second release is still faster regardless of the latches
    @Test
    void testPeekReturnsEmptyOptionalIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasReleased = new CountDownLatch(1);
        AtomicReference<D> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<D, EnqueueDecision<D>> blockingTask = letter -> {
            try {
                isBlocking.countDown();
                //noinspection ResultOfMethodCallIgnored
                hasReleased.await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resultLetter.set(letter);
            return Decisions.evict();
        };
        Function<D, EnqueueDecision<D>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        I testId = generateQueueId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);

        Thread blockingRelease = new Thread(() -> testSubject.release(blockingTask));
        blockingRelease.start();
        assertTrue(isBlocking.await(10, TimeUnit.MILLISECONDS));

        boolean result = testSubject.release(nonBlockingTask);
        assertFalse(result);
        assertFalse(invoked.get());

        hasReleased.countDown();
        blockingRelease.join();
        assertLetter(testLetter, resultLetter.get());
    }

    */


    @Test
    void testBuildWithNullDeadLetterQueueThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        //noinspection ConstantConditions
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
    void testBuildWithNullEnqueuePolicyThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.enqueuePolicy(null));
    }

    @Test
    void testBuildWithNullProcessingGroupThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        //noinspection ConstantConditions
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

        //noinspection ConstantConditions
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

        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.listenerInvocationErrorHandler(null));
    }

    // This stub TransactionManager is used for spying.
    private static class StubTransactionManager implements TransactionManager {

        @Override
        public Transaction startTransaction() {
            return NoTransactionManager.INSTANCE.startTransaction();
        }
    }

    private static void assertLetter(DeadLetter<EventMessage<?>> expected, DeadLetter<EventMessage<?>> result) {
        assertEquals(expected.sequenceIdentifier(), result.sequenceIdentifier());
        assertEquals(expected.message(), result.message());
        assertEquals(expected.cause(), result.cause());
        assertEquals(expected.enqueuedAt(), result.enqueuedAt());
        assertEquals(expected.lastTouched(), result.lastTouched());
        assertEquals(expected.diagnostic(), result.diagnostic());
    }
}
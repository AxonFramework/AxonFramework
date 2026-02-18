/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.core.unitofwork.transaction.NoTransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.Transaction;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventMessageHandler;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.core.sequencing.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.SequentialPerAggregatePolicy;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.axonframework.messaging.eventhandling.DomainEventTestUtils.createDomainEvent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DeadLetteringEventHandlerInvoker}.
 *
 * @author Steven van Beelen
 */
@Disabled("TODO #3517")
class DeadLetteringEventHandlerInvokerTest {

    private static final DomainEventMessage TEST_EVENT = createDomainEvent();
    private static final Object TEST_SEQUENCE_ID = TEST_EVENT.getAggregateIdentifier();
    private static final DeadLetter<EventMessage> TEST_DEAD_LETTER =
            new GenericDeadLetter<>(TEST_SEQUENCE_ID, TEST_EVENT);

    private EventMessageHandler handler;
    private SequencingPolicy sequencingPolicy;
    private SequencedDeadLetterQueue<EventMessage> queue;
    private EnqueuePolicy<EventMessage> enqueuePolicy;
    private TransactionManager transactionManager;

    private DeadLetteringEventHandlerInvoker testSubject;

    @BeforeEach
    void setUp() {
        handler = mock(EventMessageHandler.class);
        sequencingPolicy = spy(SequentialPerAggregatePolicy.INSTANCE);
        //noinspection unchecked
        queue = mock(SequencedDeadLetterQueue.class);
        //noinspection unchecked
        enqueuePolicy = mock(EnqueuePolicy.class);
        when(enqueuePolicy.decide(any(), any())).thenReturn(Decisions.ignore());
        transactionManager = spy(new StubTransactionManager());

        setTestSubject(createTestSubject());
        LegacyDefaultUnitOfWork.startAndGet(null);
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.clear(CurrentUnitOfWork.get());
        }
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
                                                .queue(queue)
                                                .enqueuePolicy(enqueuePolicy)
                                                .transactionManager(transactionManager);
        return customization.apply(invokerBuilder).build();
    }

    @Test
    void handleMethodHandlesEventJustFine() throws Exception {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        DeadLetter<EventMessage> expectedIfPresentLetter = new GenericDeadLetter<>(TEST_SEQUENCE_ID, TEST_EVENT);

        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        ProcessingContext context = StubProcessingContext.forMessage(TEST_EVENT);
        testSubject.handle(TEST_EVENT, context, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(eq(TEST_EVENT), any(ProcessingContext.class));
        verify(handler).handleSync(TEST_EVENT, context);

        //noinspection unchecked
        ArgumentCaptor<Supplier<DeadLetter<? extends EventMessage>>> enqueueIfPresentCaptor =
                ArgumentCaptor.forClass(Supplier.class);
        verify(queue).enqueueIfPresent(eq(TEST_SEQUENCE_ID), enqueueIfPresentCaptor.capture());
        assertLetter(expectedIfPresentLetter, enqueueIfPresentCaptor.getValue().get());

        verify(queue, never()).enqueue(eq(TEST_SEQUENCE_ID), any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void handleMethodHandlesEventJustFineWithCacheWhenDlqEmpty() throws Exception {
        setTestSubject(createTestSubject(DeadLetteringEventHandlerInvoker.Builder::enableSequenceIdentifierCache));
        doReturn(0L).when(queue).amountOfSequences();
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        ProcessingContext context = StubProcessingContext.forMessage(TEST_EVENT);
        testSubject.handle(TEST_EVENT, context, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(eq(TEST_EVENT), any(ProcessingContext.class));
        verify(handler).handleSync(TEST_EVENT, context);

        verify(queue, never()).enqueueIfPresent(eq(TEST_SEQUENCE_ID), any());

        verify(queue, never()).enqueue(eq(TEST_SEQUENCE_ID), any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void handleMethodHandlesEventJustFineWithCacheWhenDlqNotEmpty() throws Exception {
        setTestSubject(createTestSubject(DeadLetteringEventHandlerInvoker.Builder::enableSequenceIdentifierCache));
        doReturn(1L).when(queue).amountOfSequences();
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);
        ProcessingContext context = StubProcessingContext.forMessage(TEST_EVENT);
        testSubject.handle(TEST_EVENT, context, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(eq(TEST_EVENT), any(ProcessingContext.class));
        verify(handler).handleSync(TEST_EVENT, context);

        verify(queue, times(1)).enqueueIfPresent(eq(TEST_SEQUENCE_ID), any());

        verify(queue, never()).enqueue(eq(TEST_SEQUENCE_ID), any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void handleMethodHandlesEventJustFineWithCacheWhenDlqNotEmptyKeepsTrackNotInDlq() throws Exception {
        setTestSubject(createTestSubject(DeadLetteringEventHandlerInvoker.Builder::enableSequenceIdentifierCache));
        doReturn(1L).when(queue).amountOfSequences();
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        DomainEventMessage eventMessageOne = createDomainEvent("foo", 2);
        DomainEventMessage eventMessageTwo = createDomainEvent("bar", 2);
        DomainEventMessage eventMessageThree = createDomainEvent("foo", 3);

        testSubject.handle(eventMessageOne, StubProcessingContext.forMessage(eventMessageOne), Segment.ROOT_SEGMENT);
        testSubject.handle(eventMessageTwo, StubProcessingContext.forMessage(eventMessageTwo), Segment.ROOT_SEGMENT);
        testSubject.handle(eventMessageThree,
                           StubProcessingContext.forMessage(eventMessageThree),
                           Segment.ROOT_SEGMENT);

        verify(queue, times(1)).enqueueIfPresent(eq("foo"), any());
        verify(queue, times(1)).enqueueIfPresent(eq("bar"), any());

        verify(queue, never()).enqueue(eq(TEST_SEQUENCE_ID), any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void handleMethodHandlesEventJustFineWithCacheWhenDlqNotEmptyAndRespectCacheSize() throws Exception {
        setTestSubject(createTestSubject(b -> b
                .enableSequenceIdentifierCache()
                .sequenceIdentifierCacheSize(1)));
        doReturn(1L).when(queue).amountOfSequences();
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        DomainEventMessage eventMessageOne = createDomainEvent("foo", 2);
        DomainEventMessage eventMessageTwo = createDomainEvent("bar", 2);
        DomainEventMessage eventMessageThree = nextMessage(eventMessageOne);

        testSubject.handle(eventMessageOne, StubProcessingContext.forMessage(eventMessageOne), Segment.ROOT_SEGMENT);
        // as eventMessageTwo has a different sequence identifier, and the size of the sequenceIdentifierCache is set
        // to just 1, we expect the object identifier of eventMessageOne to be removed.
        testSubject.handle(eventMessageTwo, StubProcessingContext.forMessage(eventMessageTwo), Segment.ROOT_SEGMENT);
        testSubject.handle(eventMessageThree,
                           StubProcessingContext.forMessage(eventMessageThree),
                           Segment.ROOT_SEGMENT);

        verify(queue, times(2)).enqueueIfPresent(eq("foo"), any());
        verify(queue, times(1)).enqueueIfPresent(eq("bar"), any());

        verify(queue, never()).enqueue(eq(TEST_SEQUENCE_ID), any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void handleMethodHandlesEventJustFineWithCacheTryAgainToQueueAfterCleaned() throws Exception {
        setTestSubject(createTestSubject(DeadLetteringEventHandlerInvoker.Builder::enableSequenceIdentifierCache));
        doReturn(1L).when(queue).amountOfSequences();
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        testSubject.handle(TEST_EVENT, StubProcessingContext.forMessage(TEST_EVENT), Segment.ROOT_SEGMENT);
        testSubject.segmentReleased(Segment.ROOT_SEGMENT);
        testSubject.handle(nextMessage(TEST_EVENT), StubProcessingContext.forMessage(TEST_EVENT), Segment.ROOT_SEGMENT);

        verify(queue, times(2)).enqueueIfPresent(eq(TEST_SEQUENCE_ID), any());

        verify(queue, never()).enqueue(eq(TEST_SEQUENCE_ID), any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void handleMethodIgnoresEventForNonMatchingSegment() throws Exception {
        Segment testSegment = mock(Segment.class);
        when(testSegment.matches(any())).thenReturn(false);

        testSubject.handle(TEST_EVENT, new StubProcessingContext(), testSegment);

        verify(sequencingPolicy).getSequenceIdentifierFor(eq(TEST_EVENT), any(ProcessingContext.class));
        verifyNoInteractions(handler);
        verifyNoInteractions(queue);
        verifyNoInteractions(transactionManager);
    }

    @Test
    void handleMethodEnqueuesOnShouldEnqueueDecisionWhenDelegateThrowsAnException() throws Exception {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        RuntimeException testCause = new RuntimeException("some-cause");
        DeadLetter<EventMessage> expectedIfPresentLetter = new GenericDeadLetter<>(TEST_SEQUENCE_ID, TEST_EVENT);
        DeadLetter<EventMessage> expectedEnqueuedLetter =
                new GenericDeadLetter<>(TEST_SEQUENCE_ID, TEST_EVENT, testCause);

        ProcessingContext context = StubProcessingContext.forMessage(TEST_EVENT);
        doThrow(testCause).when(handler).handleSync(TEST_EVENT, context);
        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        testSubject.handle(TEST_EVENT, context, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(eq(TEST_EVENT), any(ProcessingContext.class));
        verify(handler).handleSync(TEST_EVENT, context);

        //noinspection unchecked
        ArgumentCaptor<Supplier<DeadLetter<? extends EventMessage>>> enqueueIfPresentCaptor =
                ArgumentCaptor.forClass(Supplier.class);
        verify(queue).enqueueIfPresent(eq(TEST_SEQUENCE_ID), enqueueIfPresentCaptor.capture());
        assertLetter(expectedIfPresentLetter, enqueueIfPresentCaptor.getValue().get());

        //noinspection unchecked
        ArgumentCaptor<DeadLetter<EventMessage>> policyCaptor = ArgumentCaptor.forClass(DeadLetter.class);
        verify(enqueuePolicy).decide(policyCaptor.capture(), eq(testCause));
        assertLetter(expectedEnqueuedLetter, policyCaptor.getValue());

        //noinspection unchecked
        ArgumentCaptor<DeadLetter<EventMessage>> enqueueCaptor = ArgumentCaptor.forClass(DeadLetter.class);
        verify(queue).enqueue(eq(TEST_SEQUENCE_ID), enqueueCaptor.capture());
        assertLetter(expectedEnqueuedLetter, enqueueCaptor.getValue());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void cacheKeepsTrackEnqueuedLetters() throws Exception {
        setTestSubject(createTestSubject(DeadLetteringEventHandlerInvoker.Builder::enableSequenceIdentifierCache));
        doReturn(0L).when(queue).amountOfSequences();
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        ProcessingContext context = StubProcessingContext.forMessage(TEST_EVENT);

        RuntimeException testCause = new RuntimeException("some-cause");

        doThrow(testCause).when(handler).handleSync(TEST_EVENT, context);
        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);

        testSubject.handle(TEST_EVENT, context, Segment.ROOT_SEGMENT);
        testSubject.handle(nextMessage(TEST_EVENT), context, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(eq(TEST_EVENT), any(ProcessingContext.class));
        verify(handler).handleSync(TEST_EVENT, context);

        verify(queue, times(1)).enqueueIfPresent(eq(TEST_SEQUENCE_ID), any());
    }


    @Test
    void handleMethodDoesNotEnqueueForShouldNotEnqueueDecisionWhenDelegateThrowsAnException() throws Exception {
        when(enqueuePolicy.decide(any(), any())).thenReturn(Decisions.doNotEnqueue());

        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        RuntimeException testCause = new RuntimeException("some-cause");
        DeadLetter<EventMessage> expectedIfPresentLetter =
                new GenericDeadLetter<>(TEST_SEQUENCE_ID, TEST_EVENT);
        DeadLetter<EventMessage> expectedEnqueuedLetter =
                new GenericDeadLetter<>(TEST_SEQUENCE_ID, TEST_EVENT, testCause);
        ProcessingContext context = StubProcessingContext.forMessage(TEST_EVENT);

        doThrow(testCause).when(handler).handleSync(TEST_EVENT, context);
        when(queue.enqueueIfPresent(any(), any())).thenReturn(false);
        testSubject.handle(TEST_EVENT, context, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(eq(TEST_EVENT), any(ProcessingContext.class));
        verify(handler).handleSync(TEST_EVENT, context);

        //noinspection unchecked
        ArgumentCaptor<Supplier<DeadLetter<? extends EventMessage>>> enqueueIfPresentCaptor =
                ArgumentCaptor.forClass(Supplier.class);
        verify(queue).enqueueIfPresent(eq(TEST_SEQUENCE_ID), enqueueIfPresentCaptor.capture());
        assertLetter(expectedIfPresentLetter, enqueueIfPresentCaptor.getValue().get());

        //noinspection unchecked
        ArgumentCaptor<DeadLetter<EventMessage>> policyCaptor = ArgumentCaptor.forClass(DeadLetter.class);
        verify(enqueuePolicy).decide(policyCaptor.capture(), eq(testCause));
        assertLetter(expectedEnqueuedLetter, policyCaptor.getValue());

        verify(queue, never()).enqueue(eq(TEST_SEQUENCE_ID), any());
        verifyNoInteractions(transactionManager);
    }

    @Test
    void handleMethodDoesNotHandleEventOnDelegateWhenEnqueueIfPresentReturnsTrue() throws Exception {
        when(queue.enqueueIfPresent(any(), any())).thenReturn(true);
        ProcessingContext context = StubProcessingContext.forMessage(TEST_EVENT);
        testSubject.handle(TEST_EVENT, context, Segment.ROOT_SEGMENT);

        verify(sequencingPolicy, times(2)).getSequenceIdentifierFor(eq(TEST_EVENT), any(ProcessingContext.class));
        verify(handler, never()).handleSync(TEST_EVENT, context);
        verify(queue, never()).enqueue(TEST_SEQUENCE_ID, TEST_DEAD_LETTER);
        verifyNoInteractions(transactionManager);
    }

    @Test
    void performResetOnlyInvokesParentWhenAllowResetSetToFalse() {
        setTestSubject(createTestSubject(builder -> builder.allowReset(false)));

        testSubject.performReset(null);

        verifyNoInteractions(queue);
        verifyNoInteractions(transactionManager);
        verify(handler).prepareReset(null, null);
    }

    @Test
    void performResetClearsOutTheQueueWhenAllowResetSetToTrue() {
        setTestSubject(createTestSubject(builder -> builder.allowReset(true)));

        testSubject.performReset(null);

        verify(queue).clear();
        verify(transactionManager).executeInTransaction(any());
        verify(handler).prepareReset(null, null);
    }

    @Test
    void performResetWithContextOnlyInvokesParentForAllowResetSetToFalse() {
        setTestSubject(createTestSubject(builder -> builder.allowReset(false)));

        String testContext = "some-reset-context";

        testSubject.performReset(testContext, null);

        verifyNoInteractions(queue);
        verifyNoInteractions(transactionManager);
        verify(handler).prepareReset(testContext, null);
    }

    @Test
    void performResetWithContextClearsOutTheQueueForAllowResetSetToTrue() {
        setTestSubject(createTestSubject(builder -> builder.allowReset(true)));

        String testContext = "some-reset-context";

        testSubject.performReset(testContext, null);

        verify(queue).clear();
        verify(transactionManager).executeInTransaction(any());
        verify(handler).prepareReset(testContext, null);
    }

    @Test
    void processAnyLettersReturnsFalseWhenFirstInvocationReturnsFalse() {
        when(queue.process(any(), any())).thenReturn(false);

        boolean result = testSubject.processAny();

        assertFalse(result);
        verify(transactionManager).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<? extends EventMessage>>> filterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        verify(queue).process(filterCaptor.capture(), any());

        Predicate<DeadLetter<? extends EventMessage>> letterFilter = filterCaptor.getValue();
        assertTrue(letterFilter.test(null));
    }

    @Test
    void processAnyLettersReturnsTrueWhenFirstInvocationReturnsTrue() {
        DeadLetter<EventMessage> testDeadLetter =
                new GenericDeadLetter<>("expectedIdentifier", EventTestUtils.asEventMessage("payload"));

        when(queue.process(any(), any())).thenReturn(true)
                                         .thenReturn(false);

        boolean result = testSubject.processAny();

        assertTrue(result);
        verify(transactionManager).startTransaction();


        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<? extends EventMessage>>> filterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Function<DeadLetter<? extends EventMessage>, EnqueueDecision<EventMessage>>> taskFilterCaptor =
                ArgumentCaptor.forClass(Function.class);

        verify(queue).process(filterCaptor.capture(), taskFilterCaptor.capture());

        // Invoking the first processing task will set the sequenceIdentifier for subsequent invocations.
        // This allows thorough validation of the second sequenceIdentifierFilter.
        taskFilterCaptor.getAllValues().get(0).apply(testDeadLetter);

        filterCaptor.getAllValues().forEach(letterFilter -> assertTrue(letterFilter.test(null)));
    }

    @Test
    void processLettersMatchingSequenceReturnsFalseWhenFirstInvocationReturnsFalse() {
        AtomicBoolean filterInvoked = new AtomicBoolean();
        Predicate<DeadLetter<? extends EventMessage>> testFilter = letter -> {
            filterInvoked.set(true);
            return true;
        };
        when(queue.process(any(), any())).thenReturn(false);

        boolean result = testSubject.process(testFilter);

        assertFalse(result);
        verify(transactionManager).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<? extends EventMessage>>> filterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        verify(queue).process(filterCaptor.capture(), any());

        Predicate<DeadLetter<? extends EventMessage>> letterFilter = filterCaptor.getValue();
        assertTrue(letterFilter.test(null));
        assertTrue(filterInvoked.get());
    }

    @Test
    void processLettersMatchingSequenceReturnsTrueWhenFirstInvocationReturnsTrue() {
        DeadLetter<EventMessage> testDeadLetter =
                new GenericDeadLetter<>("expectedIdentifier", EventTestUtils.asEventMessage("payload"));

        AtomicBoolean filterInvoked = new AtomicBoolean();
        Predicate<DeadLetter<? extends EventMessage>> testFilter = letter -> {
            filterInvoked.set(true);
            return true;
        };
        when(queue.process(any(), any())).thenReturn(true)
                                         .thenReturn(false);

        boolean result = testSubject.process(testFilter);

        assertTrue(result);
        verify(transactionManager).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<? extends EventMessage>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Function<DeadLetter<? extends EventMessage>, EnqueueDecision<EventMessage>>> taskFilterCaptor =
                ArgumentCaptor.forClass(Function.class);

        verify(queue).process(letterFilterCaptor.capture(), taskFilterCaptor.capture());

        // Invoking the first processing task will set the sequenceIdentifier for subsequent invocations.
        // This allows thorough validation of the second sequenceIdentifierFilter.
        taskFilterCaptor.getAllValues().get(0).apply(testDeadLetter);

        letterFilterCaptor.getAllValues().forEach(letterFilter -> assertTrue(letterFilter.test(null)));
        assertTrue(filterInvoked.get());
    }

    @Test
    void buildWithNullDeadLetterQueueThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.queue(null));
    }

    @Test
    void buildWithoutDeadLetterQueueThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder()
                                                .transactionManager(NoTransactionManager.instance());

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithNullEnqueuePolicyThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject = DeadLetteringEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.enqueuePolicy(null));
    }

    @Test
    void buildWithNullTransactionManagerThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder();

        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.transactionManager(null));
    }

    @Test
    void buildWithoutTransactionManagerThrowsAxonConfigurationException() {
        DeadLetteringEventHandlerInvoker.Builder builderTestSubject =
                DeadLetteringEventHandlerInvoker.builder()
                                                .queue(queue);

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithNullListenerInvocationErrorHandlerThrowsAxonConfigurationException() {
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

    private static void assertLetter(DeadLetter<? extends EventMessage> expected,
                                     DeadLetter<? extends EventMessage> result) {
        assertEquals(expected.message(), result.message());
        assertEquals(expected.cause(), result.cause());
        assertEquals(expected.enqueuedAt(), result.enqueuedAt());
        assertEquals(expected.lastTouched(), result.lastTouched());
        assertEquals(expected.diagnostics(), result.diagnostics());
    }

    private static DomainEventMessage nextMessage(DomainEventMessage domainEventMessage) {
        return createDomainEvent(domainEventMessage.getAggregateIdentifier(), domainEventMessage.getSequenceNumber() + 1L);
    }
}

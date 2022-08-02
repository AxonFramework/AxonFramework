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
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequenceIdentifier;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.utils.EventTestUtils;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
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
    void testGroup() {
        assertEquals(TEST_PROCESSING_GROUP, testSubject.group());
    }

    @Test
    void testProcessAnyReturnsFalseWhenFirstInvocationReturnsFalse() {
        when(queue.process(any(), any(), any())).thenReturn(false);

        boolean result = testSubject.processAny();

        assertFalse(result);
        verify(transactionManager).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<SequenceIdentifier>> sequenceFilterCaptor = ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<EventMessage<?>>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        verify(queue).process(sequenceFilterCaptor.capture(), letterFilterCaptor.capture(), any());

        Predicate<SequenceIdentifier> sequenceFilter = sequenceFilterCaptor.getValue();
        assertTrue(sequenceFilter.test(new EventSequencedIdentifier("dont-care", testSubject.group())));

        Predicate<DeadLetter<EventMessage<?>>> letterFilter = letterFilterCaptor.getValue();
        assertTrue(letterFilter.test(null));
    }

    @Test
    void testProcessAnyReturnsTrueWhenFirstInvocationReturnsTrue() {
        EventSequencedIdentifier expectedIdentifier = new EventSequencedIdentifier("test-id", testSubject.group());
        DeadLetter<EventMessage<?>> testDeadLetter =
                new GenericDeadLetter<>(expectedIdentifier, GenericEventMessage.asEventMessage("payload"));

        when(queue.process(any(), any(), any())).thenReturn(true)
                                                .thenReturn(false);

        boolean result = testSubject.processAny();

        assertTrue(result);
        verify(transactionManager, times(2)).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<SequenceIdentifier>> sequenceFilterCaptor = ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<EventMessage<?>>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Function<DeadLetter<EventMessage<?>>, EnqueueDecision<DeadLetter<EventMessage<?>>>>> taskFilterCaptor =
                ArgumentCaptor.forClass(Function.class);

        verify(queue, times(2)).process(sequenceFilterCaptor.capture(),
                                        letterFilterCaptor.capture(),
                                        taskFilterCaptor.capture());

        // Invoking the first processing task will set the sequenceIdentifier for subsequent invocations.
        // This allows thorough validation of the second sequenceIdentifierFilter.
        taskFilterCaptor.getAllValues().get(0).apply(testDeadLetter);

        List<Predicate<SequenceIdentifier>> sequenceFilters = sequenceFilterCaptor.getAllValues();
        assertTrue(sequenceFilters.get(0).test(new EventSequencedIdentifier("dont-care", testSubject.group())));
        assertTrue(sequenceFilters.get(1).test(expectedIdentifier));

        letterFilterCaptor.getAllValues().forEach(letterFilter -> assertTrue(letterFilter.test(null)));
    }

    @Test
    void testProcessIdentifierMatchingSequenceReturnsFalseWhenFirstInvocationReturnsFalse() {
        AtomicBoolean invokedFilter = new AtomicBoolean(false);
        Predicate<SequenceIdentifier> testFilter = id -> {
            invokedFilter.set(true);
            return true;
        };
        when(queue.process(any(), any(), any())).thenReturn(false);

        boolean result = testSubject.processIdentifierMatchingSequence(testFilter);

        assertFalse(result);
        verify(transactionManager).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<SequenceIdentifier>> sequenceFilterCaptor = ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<EventMessage<?>>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        verify(queue).process(sequenceFilterCaptor.capture(), letterFilterCaptor.capture(), any());

        Predicate<SequenceIdentifier> sequenceFilter = sequenceFilterCaptor.getValue();
        assertTrue(sequenceFilter.test(new EventSequencedIdentifier("dont-care", testSubject.group())));
        assertTrue(invokedFilter.get());

        Predicate<DeadLetter<EventMessage<?>>> letterFilter = letterFilterCaptor.getValue();
        assertTrue(letterFilter.test(null));
    }

    @Test
    void testProcessIdentifierMatchingSequenceReturnsTrueWhenFirstInvocationReturnsTrue() {
        EventSequencedIdentifier expectedIdentifier = new EventSequencedIdentifier("test-id", testSubject.group());
        DeadLetter<EventMessage<?>> testDeadLetter =
                new GenericDeadLetter<>(expectedIdentifier, GenericEventMessage.asEventMessage("payload"));

        AtomicBoolean invokedFilter = new AtomicBoolean(false);
        Predicate<SequenceIdentifier> testFilter = id -> {
            invokedFilter.set(true);
            return true;
        };
        when(queue.process(any(), any(), any())).thenReturn(true)
                                                .thenReturn(false);

        boolean result = testSubject.processIdentifierMatchingSequence(testFilter);

        assertTrue(result);
        verify(transactionManager, times(2)).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<SequenceIdentifier>> sequenceFilterCaptor = ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<EventMessage<?>>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Function<DeadLetter<EventMessage<?>>, EnqueueDecision<DeadLetter<EventMessage<?>>>>> taskFilterCaptor =
                ArgumentCaptor.forClass(Function.class);

        verify(queue, times(2)).process(sequenceFilterCaptor.capture(),
                                        letterFilterCaptor.capture(),
                                        taskFilterCaptor.capture());

        // Invoking the first processing task will set the sequenceIdentifier for subsequent invocations.
        // This allows thorough validation of the second sequenceIdentifierFilter.
        taskFilterCaptor.getAllValues().get(0).apply(testDeadLetter);

        List<Predicate<SequenceIdentifier>> sequenceFilters = sequenceFilterCaptor.getAllValues();
        assertTrue(sequenceFilters.get(0).test(new EventSequencedIdentifier("dont-care", testSubject.group())));
        assertTrue(sequenceFilters.get(1).test(expectedIdentifier));
        assertTrue(invokedFilter.get());

        letterFilterCaptor.getAllValues().forEach(letterFilter -> assertTrue(letterFilter.test(null)));
    }

    @Test
    void testProcessLetterMatchingSequenceReturnsFalseWhenFirstInvocationReturnsFalse() {
        AtomicBoolean filterInvoked = new AtomicBoolean();
        Predicate<DeadLetter<EventMessage<?>>> testFilter = letter -> {
            filterInvoked.set(true);
            return true;
        };
        when(queue.process(any(), any(), any())).thenReturn(false);

        boolean result = testSubject.processLetterMatchingSequence(testFilter);

        assertFalse(result);
        verify(transactionManager).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<SequenceIdentifier>> sequenceFilterCaptor = ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<EventMessage<?>>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        verify(queue).process(sequenceFilterCaptor.capture(), letterFilterCaptor.capture(), any());

        Predicate<SequenceIdentifier> sequenceFilter = sequenceFilterCaptor.getValue();
        assertTrue(sequenceFilter.test(new EventSequencedIdentifier("dont-care", testSubject.group())));

        Predicate<DeadLetter<EventMessage<?>>> letterFilter = letterFilterCaptor.getValue();
        assertTrue(letterFilter.test(null));
        assertTrue(filterInvoked.get());
    }

    @Test
    void testProcessLetterMatchingSequenceReturnsTrueWhenFirstInvocationReturnsTrue() {
        EventSequencedIdentifier expectedIdentifier = new EventSequencedIdentifier("test-id", testSubject.group());
        DeadLetter<EventMessage<?>> testDeadLetter =
                new GenericDeadLetter<>(expectedIdentifier, GenericEventMessage.asEventMessage("payload"));

        AtomicBoolean filterInvoked = new AtomicBoolean();
        Predicate<DeadLetter<EventMessage<?>>> testFilter = letter -> {
            filterInvoked.set(true);
            return true;
        };
        when(queue.process(any(), any(), any())).thenReturn(true)
                                                .thenReturn(false);

        boolean result = testSubject.processLetterMatchingSequence(testFilter);

        assertTrue(result);
        verify(transactionManager, times(2)).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<SequenceIdentifier>> sequenceFilterCaptor = ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<EventMessage<?>>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Function<DeadLetter<EventMessage<?>>, EnqueueDecision<DeadLetter<EventMessage<?>>>>> taskFilterCaptor =
                ArgumentCaptor.forClass(Function.class);

        verify(queue, times(2)).process(sequenceFilterCaptor.capture(),
                                        letterFilterCaptor.capture(),
                                        taskFilterCaptor.capture());

        // Invoking the first processing task will set the sequenceIdentifier for subsequent invocations.
        // This allows thorough validation of the second sequenceIdentifierFilter.
        taskFilterCaptor.getAllValues().get(0).apply(testDeadLetter);

        List<Predicate<SequenceIdentifier>> sequenceFilters = sequenceFilterCaptor.getAllValues();
        assertTrue(sequenceFilters.get(0).test(new EventSequencedIdentifier("dont-care", testSubject.group())));
        assertTrue(sequenceFilters.get(1).test(expectedIdentifier));

        letterFilterCaptor.getAllValues().forEach(letterFilter -> assertTrue(letterFilter.test(null)));
        assertTrue(filterInvoked.get());
    }

    @Test
    void testProcessReturnsFalseWhenFirstInvocationReturnsFalse() {
        AtomicBoolean idFilterInvoked = new AtomicBoolean();
        Predicate<SequenceIdentifier> testIdFilter = id -> {
            idFilterInvoked.set(true);
            return true;
        };
        AtomicBoolean letterFilterInvoked = new AtomicBoolean();
        Predicate<DeadLetter<EventMessage<?>>> testLetterFilter = letter -> {
            letterFilterInvoked.set(true);
            return true;
        };
        when(queue.process(any(), any(), any())).thenReturn(false);

        boolean result = testSubject.process(testIdFilter, testLetterFilter);

        assertFalse(result);
        verify(transactionManager).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<SequenceIdentifier>> sequenceFilterCaptor = ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<EventMessage<?>>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        verify(queue).process(sequenceFilterCaptor.capture(), letterFilterCaptor.capture(), any());

        Predicate<SequenceIdentifier> sequenceFilter = sequenceFilterCaptor.getValue();
        assertTrue(sequenceFilter.test(new EventSequencedIdentifier("dont-care", testSubject.group())));
        assertTrue(idFilterInvoked.get());

        Predicate<DeadLetter<EventMessage<?>>> letterFilter = letterFilterCaptor.getValue();
        assertTrue(letterFilter.test(null));
        assertTrue(letterFilterInvoked.get());
    }

    @Test
    void testProcessReturnsTrueWhenFirstInvocationReturnsTrue() {
        EventSequencedIdentifier expectedIdentifier = new EventSequencedIdentifier("test-id", testSubject.group());
        DeadLetter<EventMessage<?>> testDeadLetter =
                new GenericDeadLetter<>(expectedIdentifier, GenericEventMessage.asEventMessage("payload"));
        AtomicBoolean idFilterInvoked = new AtomicBoolean();
        Predicate<SequenceIdentifier> testIdFilter = id -> {
            idFilterInvoked.set(true);
            return true;
        };
        AtomicBoolean letterFilterInvoked = new AtomicBoolean();
        Predicate<DeadLetter<EventMessage<?>>> testLetterFilter = letter -> {
            letterFilterInvoked.set(true);
            return true;
        };
        when(queue.process(any(), any(), any())).thenReturn(true)
                                                .thenReturn(false);

        boolean result = testSubject.process(testIdFilter, testLetterFilter);

        assertTrue(result);
        verify(transactionManager, times(2)).startTransaction();

        //noinspection unchecked
        ArgumentCaptor<Predicate<SequenceIdentifier>> sequenceFilterCaptor = ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Predicate<DeadLetter<EventMessage<?>>>> letterFilterCaptor =
                ArgumentCaptor.forClass(Predicate.class);
        //noinspection unchecked
        ArgumentCaptor<Function<DeadLetter<EventMessage<?>>, EnqueueDecision<DeadLetter<EventMessage<?>>>>> taskFilterCaptor =
                ArgumentCaptor.forClass(Function.class);

        verify(queue, times(2)).process(sequenceFilterCaptor.capture(),
                                        letterFilterCaptor.capture(),
                                        taskFilterCaptor.capture());
        // Invoking the first processing task will set the sequenceIdentifier for subsequent invocations.
        // This allows thorough validation of the second sequenceIdentifierFilter.
        taskFilterCaptor.getAllValues().get(0).apply(testDeadLetter);

        List<Predicate<SequenceIdentifier>> sequenceFilters = sequenceFilterCaptor.getAllValues();
        assertTrue(sequenceFilters.get(0).test(new EventSequencedIdentifier("dont-care", testSubject.group())));
        assertTrue(sequenceFilters.get(1).test(expectedIdentifier));
        assertTrue(idFilterInvoked.get());

        letterFilterCaptor.getAllValues().forEach(letterFilter -> assertTrue(letterFilter.test(null)));
        assertTrue(letterFilterInvoked.get());
    }

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
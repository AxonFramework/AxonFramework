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

package org.axonframework.messaging.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract class providing a generic test suite every {@link SequencedDeadLetterQueue} implementation should comply
 * with.
 *
 * @param <M> The {@link DeadLetter} implementation enqueued by this test class.
 * @author Steven van Beelen
 */
public abstract class SequencedDeadLetterQueueTest<M extends Message<?>> {

    private SequencedDeadLetterQueue<M> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = buildTestSubject();
    }

    /**
     * Constructs the {@link SequencedDeadLetterQueue} implementation under test.
     *
     * @return A {@link SequencedDeadLetterQueue} implementation under test.
     */
    protected abstract SequencedDeadLetterQueue<M> buildTestSubject();

    @Test
    void testEnqueue() {
        Object testId = generateId();
        DeadLetter<? extends M> testLetter = generateInitialLetter();

        testSubject.enqueue(testId, testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequencesIsReached() {
        long maxSequences = testSubject.maxSequences();
        assertTrue(maxSequences > 0);

        for (int i = 0; i < maxSequences; i++) {
            testSubject.enqueue(generateId(), generateInitialLetter());
        }

        String oneSequenceToMany = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        assertThrows(DeadLetterQueueOverflowException.class, () -> testSubject.enqueue(oneSequenceToMany, testLetter));
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequenceSizeIsReached() {
        Object testId = generateId();

        long maxSequenceSize = testSubject.maxSequenceSize();
        assertTrue(maxSequenceSize > 0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(testId, generateInitialLetter());
        }

        DeadLetter<M> oneLetterToMany = generateInitialLetter();
        assertThrows(DeadLetterQueueOverflowException.class, () -> testSubject.enqueue(testId, oneLetterToMany));
    }

    @Test
    void testEnqueueIfPresentThrowsDeadLetterQueueOverflowExceptionForFullQueue() {
        Object testId = generateId();

        long maxSequenceSize = testSubject.maxSequenceSize();
        assertTrue(maxSequenceSize > 0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(testId, generateInitialLetter());
        }

        assertThrows(DeadLetterQueueOverflowException.class,
                     () -> testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter));
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForEmptyQueue() {
        Object testId = generateId();

        boolean result = testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter);

        assertFalse(result);
        assertFalse(testSubject.contains(testId));
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForNonExistentSequenceIdentifier() {
        Object testFirstId = generateId();

        testSubject.enqueue(testFirstId, generateInitialLetter());

        Object testSecondId = generateId();

        boolean result = testSubject.enqueueIfPresent(testSecondId, this::generateFollowUpLetter);

        assertFalse(result);
        assertTrue(testSubject.contains(testFirstId));
        assertFalse(testSubject.contains(testSecondId));
    }

    @Test
    void testEnqueueIfPresentEnqueuesForExistingSequenceIdentifier() {
        Object testId = generateId();
        DeadLetter<M> testFirstLetter = generateInitialLetter();
        DeadLetter<M> testSecondLetter = generateFollowUpLetter();

        testSubject.enqueue(testId, testFirstLetter);
        testSubject.enqueueIfPresent(testId, () -> testSecondLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(testFirstLetter, resultLetters.next());
        assertTrue(resultLetters.hasNext());
        assertLetter(testSecondLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testEvictDoesNotChangeTheQueueForNonExistentSequenceIdentifier() {
        Object testId = generateId();
        DeadLetter<? extends M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());

        testSubject.evict(mapToQueueImplementation(generateInitialLetter()));

        assertTrue(testSubject.contains(testId));
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testEvictDoesNotChangeTheQueueForNonExistentLetterIdentifier() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        DeadLetter<? extends M> actualLetter = resultLetters.next();
        assertLetter(testLetter, actualLetter);
        assertFalse(resultLetters.hasNext());

        testSubject.evict(mapToQueueImplementation(generateInitialLetter()));

        assertTrue(testSubject.contains(testId));
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testEvictRemovesLetterFromQueue() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();

        testSubject.enqueue(testId, testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        DeadLetter<? extends M> resultLetter = resultLetters.next();
        assertLetter(testLetter, resultLetter);
        assertFalse(resultLetters.hasNext());

        testSubject.evict(resultLetter);

        assertFalse(testSubject.contains(testId));
        assertFalse(testSubject.deadLetters().iterator().hasNext());
    }

    @Test
    void testRequeueThrowsNoSuchDeadLetterExceptionForNonExistentSequenceIdentifier() {
        DeadLetter<M> testLetter = generateInitialLetter();

        assertThrows(NoSuchDeadLetterException.class,
                     () -> testSubject.requeue(mapToQueueImplementation(testLetter), l -> l));
    }

    @Test
    void testRequeueThrowsNoSuchDeadLetterExceptionForNonExistentLetterIdentifier() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        DeadLetter<M> otherTestLetter = generateInitialLetter();

        testSubject.enqueue(testId, testLetter);

        assertThrows(NoSuchDeadLetterException.class,
                     () -> testSubject.requeue(mapToQueueImplementation(otherTestLetter), l -> l));
    }

    @Test
    void testRequeueReenterLetterToQueueWithUpdatedLastTouchedAndCause() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        Throwable testCause = generateThrowable();

        testSubject.enqueue(testId, testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        DeadLetter<? extends M> resultLetter = resultLetters.next();
        assertLetter(testLetter, resultLetter);
        assertFalse(resultLetters.hasNext());

        DeadLetter<M> expectedLetter = generateRequeuedLetter(testLetter, testCause);

        testSubject.requeue(resultLetter, l -> l.withCause(testCause));

        assertTrue(testSubject.contains(testId));
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testContains() {
        Object testId = generateId();
        Object otherTestId = generateId();

        assertFalse(testSubject.contains(testId));

        testSubject.enqueue(testId, generateInitialLetter());

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.contains(otherTestId));
    }

    @Test
    void testDeadLettersPerSequenceIdentifier() {
        Object testId = generateId();
        DeadLetter<M> expected = generateInitialLetter();

        Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultIterator.hasNext());

        testSubject.enqueue(testId, expected);

        resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultIterator.hasNext());
        assertLetter(expected, resultIterator.next());
        assertFalse(resultIterator.hasNext());
    }

    @Test
    void testDeadLetters() {
        Object thisTestId = generateId();
        Object thatTestId = generateId();

        Iterator<Iterable<DeadLetter<? extends M>>> result = testSubject.deadLetters().iterator();
        assertFalse(result.hasNext());

        DeadLetter<? extends M> thisFirstExpected = generateInitialLetter();
        DeadLetter<? extends M> thisSecondExpected = generateInitialLetter();
        DeadLetter<? extends M> thatFirstExpected = generateInitialLetter();
        DeadLetter<? extends M> thatSecondExpected = generateInitialLetter();

        testSubject.enqueue(thisTestId, thisFirstExpected);
        testSubject.enqueue(thatTestId, thatFirstExpected);
        testSubject.enqueue(thisTestId, thisSecondExpected);
        testSubject.enqueue(thatTestId, thatSecondExpected);

        result = testSubject.deadLetters().iterator();

        while (result.hasNext()) {
            Iterator<DeadLetter<? extends M>> resultLetters = result.next().iterator();
            while (resultLetters.hasNext()) {
                DeadLetter<? extends M> resultLetter = resultLetters.next();
                if (equals(thisFirstExpected).test(resultLetter)) {
                    assertLetter(thisFirstExpected, resultLetter);
                    assertTrue(resultLetters.hasNext());
                    assertLetter(thisSecondExpected, resultLetters.next());
                    assertFalse(resultLetters.hasNext());
                } else {
                    assertLetter(thatFirstExpected, resultLetter);
                    assertTrue(resultLetters.hasNext());
                    assertLetter(thatSecondExpected, resultLetters.next());
                    assertFalse(resultLetters.hasNext());
                }
            }
        }
    }

    @Test
    void testIsFullReturnsTrueAfterMaximumAmountOfSequencesIsReached() {
        assertFalse(testSubject.isFull(generateId()));

        long maxSequences = testSubject.maxSequences();
        assertTrue(maxSequences > 0);

        for (int i = 0; i < maxSequences; i++) {
            testSubject.enqueue(generateId(), generateInitialLetter());
        }

        assertTrue(testSubject.isFull(generateId()));
    }

    @Test
    void testIsFullReturnsTrueAfterMaximumSequenceSizeIsReached() {
        Object testId = generateId();

        assertFalse(testSubject.isFull(testId));

        long maxSequenceSize = testSubject.maxSequenceSize();
        assertTrue(maxSequenceSize > 0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(testId, generateInitialLetter());
        }

        assertTrue(testSubject.isFull(testId));
    }

    @Test
    void testProcessReturnsFalseIfThereAreNoLetters() {
        AtomicBoolean taskInvoked = new AtomicBoolean(false);
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            taskInvoked.set(true);
            return Decisions.evict();
        };

        boolean result = testSubject.process(testTask);

        assertFalse(result);
        assertFalse(taskInvoked.get());
    }


    @Test
    void testProcessReturnsTrueAndEvictsTheLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        boolean result = testSubject.process(testTask);
        assertTrue(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testProcessReturnsFalseAndRequeuesTheLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        Instant expectedLastTouched = setAndGetTime();
        DeadLetter<M> expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean result = testSubject.process(testTask);
        assertFalse(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testProcessInvokesProcessingTaskWithLastTouchedOrder() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        Object testThisId = generateId();
        DeadLetter<? extends M> testThisLetter = generateInitialLetter();
        testSubject.enqueue(testThisId, testThisLetter);

        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime();
        Object testThatId = generateId();
        DeadLetter<? extends M> testThatLetter = generateInitialLetter();
        testSubject.enqueue(testThatId, testThatLetter);

        boolean result = testSubject.process(testTask);
        assertTrue(result);
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(testTask);
        assertTrue(result);
        assertLetter(testThatLetter, resultLetter.get());
    }

    /**
     * A "claimed sequence" in this case means that a process task for a given "sequence identifier" is still processing
     * the sequence. Furthermore, if it's the sole sequence, the processing task will not be invoked. This approach
     * ensure the events for a given sequence identifier are handled in the order they've been dead-lettered (a.k.a., in
     * sequence).
     */
    @Test
    void testProcessReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasProcessed = new CountDownLatch(1);
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<DeadLetter<? extends M>, EnqueueDecision<M>> blockingTask = letter -> {
            try {
                isBlocking.countDown();
                //noinspection ResultOfMethodCallIgnored
                hasProcessed.await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resultLetter.set(letter);
            return Decisions.evict();
        };
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        Thread blockingProcess = new Thread(() -> testSubject.process(blockingTask));
        blockingProcess.start();
        assertTrue(isBlocking.await(100, TimeUnit.MILLISECONDS));

        boolean result = testSubject.process(nonBlockingTask);
        assertFalse(result);
        assertFalse(invoked.get());

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void testProcessLetterPredicateReturnsFalseIfThereAreNoMatchingLetters() {
        AtomicBoolean releasedLetter = new AtomicBoolean(false);
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            releasedLetter.set(true);
            return Decisions.evict();
        };

        testSubject.enqueue(generateId(), generateInitialLetter());
        testSubject.enqueue(generateId(), generateInitialLetter());

        boolean result = testSubject.process(letter -> false, testTask);
        assertFalse(result);
        assertFalse(releasedLetter.get());
    }

    @Test
    void testProcessLetterPredicateInvokesProcessingTaskWithMatchingLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        Object testThisId = generateId();
        DeadLetter<? extends M> testThisLetter = generateInitialLetter();
        testSubject.enqueue(testThisId, testThisLetter);

        Object testThatId = generateId();
        DeadLetter<? extends M> testThatLetter = generateInitialLetter();
        testSubject.enqueue(testThatId, testThatLetter);

        boolean result = testSubject.process(equals(testThisLetter), testTask);
        assertTrue(result);
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(equals(testThatLetter), testTask);
        assertTrue(result);
        assertLetter(testThatLetter, resultLetter.get());
    }

    private Predicate<DeadLetter<? extends M>> equals(DeadLetter<? extends M> expected) {
        return actual -> expected.message().getIdentifier().equals(actual.message().getIdentifier());
    }

    @Test
    void testProcessLetterPredicateReturnsTrueAndEvictsTheLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        Object testId = generateId();
        Object nonMatchingId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(nonMatchingId, generateInitialLetter());

        boolean result = testSubject.process(letter -> letter.message().getPayload()
                                                             .equals(testLetter.message().getPayload()), testTask);
        assertTrue(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultLetters.hasNext());
        assertTrue(testSubject.deadLetters().iterator().hasNext());
    }

    @Test
    void testProcessLetterPredicateReturnsFalseAndRequeuesTheLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        Object testId = generateId();
        Object nonMatchingId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(nonMatchingId, generateInitialLetter());

        Instant expectedLastTouched = setAndGetTime();
        DeadLetter<M> expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean result = testSubject.process(equals(testLetter), testTask);
        assertFalse(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
        assertTrue(testSubject.deadLetters().iterator().hasNext());
    }

    /**
     * A "claimed sequence" in this case means that a process task for a given "sequence identifier" is still processing
     * the sequence. Furthermore, if it's the sole sequence, the processing task will not be invoked. This approach
     * ensure the events for a given sequence identifier are handled in the order they've been dead-lettered (a.k.a., in
     * sequence).
     */
    @Test
    void testProcessLetterPredicateReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasProcessed = new CountDownLatch(1);
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<DeadLetter<? extends M>, EnqueueDecision<M>> blockingTask = letter -> {
            try {
                isBlocking.countDown();
                //noinspection ResultOfMethodCallIgnored
                hasProcessed.await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resultLetter.set(letter);
            return Decisions.evict();
        };
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        Object testId = generateId();
        Object nonMatchingId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(nonMatchingId, generateInitialLetter());

        Thread blockingProcess = new Thread(() -> testSubject.process(
                equals(testLetter),
                blockingTask
        ));
        blockingProcess.start();
        assertTrue(isBlocking.await(100, TimeUnit.MILLISECONDS));

        boolean result = testSubject.process(equals(testLetter), nonBlockingTask);
        assertFalse(result);
        assertFalse(invoked.get());

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void testClearRemovesAllEntries() {
        Object idOne = generateId();
        Object idTwo = generateId();
        Object idThree = generateId();

        assertFalse(testSubject.contains(idOne));
        assertFalse(testSubject.contains(idTwo));
        assertFalse(testSubject.contains(idThree));

        testSubject.enqueue(idOne, generateInitialLetter());
        testSubject.enqueue(idTwo, generateInitialLetter());
        testSubject.enqueue(idThree, generateInitialLetter());

        assertTrue(testSubject.contains(idOne));
        assertTrue(testSubject.contains(idTwo));
        assertTrue(testSubject.contains(idThree));

        testSubject.clear();

        assertFalse(testSubject.contains(idOne));
        assertFalse(testSubject.contains(idTwo));
        assertFalse(testSubject.contains(idThree));
    }

    /**
     * Generate a unique {@link String} based on {@link UUID#randomUUID()}.
     *
     * @return A unique {@link String}, based on {@link UUID#randomUUID()}.
     */
    protected static String generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a unique {@link EventMessage} to serves as the {@link DeadLetter#message()} contents.
     *
     * @return A unique {@link EventMessage} to serves as the {@link DeadLetter#message()} contents.
     */
    protected static EventMessage<String> generateEvent() {
        return GenericEventMessage.asEventMessage("Then this happened..." + UUID.randomUUID());
    }

    /**
     * Generate a unique {@link Throwable} by using {@link #generateId()} in the cause description.
     *
     * @return A unique {@link Throwable} by using {@link #generateId()} in the cause description.
     */
    protected static Throwable generateThrowable() {
        return new RuntimeException("Because..." + generateId());
    }

    /**
     * Generate an initial {@link DeadLetter} implementation expected by the test subject. This means the dead-letter is
     * the first in a potential sequence of letters.
     *
     * @return A {@link DeadLetter} implementation expected by the test subject.
     */
    protected abstract DeadLetter<M> generateInitialLetter();

    /**
     * Generate a follow-up {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}. This means the dead-letter is part of a sequence of letters.
     *
     * @return A follow-up {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}.
     */
    protected abstract DeadLetter<M> generateFollowUpLetter();

    protected DeadLetter<M> mapToQueueImplementation(DeadLetter<M> deadLetter) {
        return deadLetter;
    }

    /**
     * Generate a {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued. The result is expected to have an adjusted {@link DeadLetter#lastTouched()} and
     * {@link DeadLetter#cause()}. The latter should resemble the given {@code requeueCause}.
     *
     * @param original     The original {@link DeadLetter} to base the requeued dead-letter on.
     * @param requeueCause The cause for requeueing the {@code original}.
     * @return A {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued.
     */
    protected DeadLetter<M> generateRequeuedLetter(DeadLetter<M> original, Throwable requeueCause) {
        // Move time to impose changes to lastTouched.
        Instant lastTouched = setAndGetTime();
        return generateRequeuedLetter(original, lastTouched, requeueCause, MetaData.emptyInstance());
    }

    /**
     * Generate a {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued. The result is expected to have an adjusted {@link DeadLetter#lastTouched()} and
     * {@link DeadLetter#cause()}. The former should resemble the given {@code lastTouched} and the latter the given
     * {@code requeueCause}.
     *
     * @param original     The original {@link DeadLetter} to base the requeued dead-letter on.
     * @param lastTouched  The {@link DeadLetter#lastTouched()} of the generated {@link DeadLetter} implementation.
     * @param requeueCause The cause for requeueing the {@code original}.
     * @param diagnostics  The diagnostics {@link MetaData} added to the requeued letter for monitoring.
     * @return A {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued.
     */
    protected abstract DeadLetter<M> generateRequeuedLetter(DeadLetter<M> original,
                                                            Instant lastTouched,
                                                            Throwable requeueCause,
                                                            MetaData diagnostics);

    /**
     * Set the current time for testing to {@link Instant#now()} and return this {@code Instant}.
     *
     * @return {@link Instant#now()}, the current time for the invoker of this method.
     */
    protected Instant setAndGetTime() {
        return setAndGetTime(Instant.now());
    }

    /**
     * Set the current time for testing to given {@code time} and return this {@code Instant}.
     *
     * @param time The time to test under.
     * @return The given {@code time}.
     */
    protected Instant setAndGetTime(Instant time) {
        setClock(Clock.fixed(time, ZoneId.systemDefault()));
        return time;
    }

    /**
     * Set the {@link Clock} used by this test. Use this to influence the {@link DeadLetter#enqueuedAt()} and
     * {@link DeadLetter#lastTouched()} times.
     *
     * @param clock The clock to use during testing.
     */
    protected abstract void setClock(Clock clock);

    /**
     * Assert whether the {@code expected} {@link DeadLetter} matches the {@code actual} {@code DeadLetter} of type
     * {@code D}.
     *
     * @param expected The expected format of the {@link DeadLetter}.
     * @param actual   The actual format of the {@link DeadLetter}.
     */
    protected void assertLetter(DeadLetter<? extends M> expected, DeadLetter<? extends M> actual) {
        assertEquals(expected.message(), actual.message());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(expected.enqueuedAt(), actual.enqueuedAt());
        assertEquals(expected.lastTouched(), actual.lastTouched());
        assertEquals(expected.diagnostics(), actual.diagnostics());
    }
}

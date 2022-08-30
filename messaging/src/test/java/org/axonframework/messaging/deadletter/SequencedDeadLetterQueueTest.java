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
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
    abstract SequencedDeadLetterQueue<M> buildTestSubject();

    @Test
    void enqueueAddsDeadLetter() {
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
    void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequencesIsReached() {
        long maxSequences = testSubject.maxSequences();
        assertTrue(maxSequences > 0);

        for (int i = 0; i < maxSequences; i++) {
            testSubject.enqueue(generateId(), generateInitialLetter());
        }

        Object oneSequenceToMany = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        assertThrows(DeadLetterQueueOverflowException.class, () -> testSubject.enqueue(oneSequenceToMany, testLetter));
    }

    @Test
    void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequenceSizeIsReached() {
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
    void enqueueIfPresentThrowsDeadLetterQueueOverflowExceptionForFullQueue() {
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
    void enqueueIfPresentDoesNotEnqueueForEmptyQueue() {
        Object testId = generateId();

        boolean result = testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter);

        assertFalse(result);
        assertFalse(testSubject.contains(testId));
    }

    @Test
    void enqueueIfPresentDoesNotEnqueueForNonExistentSequenceIdentifier() {
        Object testFirstId = generateId();

        testSubject.enqueue(testFirstId, generateInitialLetter());

        Object testSecondId = generateId();

        boolean result = testSubject.enqueueIfPresent(testSecondId, this::generateFollowUpLetter);

        assertFalse(result);
        assertTrue(testSubject.contains(testFirstId));
        assertFalse(testSubject.contains(testSecondId));
    }

    @Test
    void enqueueIfPresentEnqueuesForExistingSequenceIdentifier() {
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
    void evictDoesNotChangeTheQueueForNonExistentSequenceIdentifier() {
        Object testId = generateId();
        DeadLetter<? extends M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertEquals(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());

        testSubject.evict(generateInitialLetter());

        assertTrue(testSubject.contains(testId));
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertEquals(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void evictDoesNotChangeTheQueueForNonExistentLetterIdentifier() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertEquals(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());

        testSubject.evict(generateInitialLetter());

        assertTrue(testSubject.contains(testId));
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertEquals(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void evictRemovesLetterFromQueue() {
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
    void requeueThrowsNoSuchDeadLetterExceptionForNonExistentSequenceIdentifier() {
        DeadLetter<M> testLetter = generateInitialLetter();

        assertThrows(NoSuchDeadLetterException.class, () -> testSubject.requeue(testLetter, l -> l));
    }

    @Test
    void requeueThrowsNoSuchDeadLetterExceptionForNonExistentLetterIdentifier() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        DeadLetter<M> otherTestLetter = generateInitialLetter();

        testSubject.enqueue(testId, testLetter);

        assertThrows(NoSuchDeadLetterException.class, () -> testSubject.requeue(otherTestLetter, l -> l));
    }

    @Test
    void requeueReentersLetterToQueueWithUpdatedLastTouchedAndCause() {
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
    void containsReturnsTrueForContainedLetter() {
        Object testId = generateId();
        Object otherTestId = generateId();

        assertFalse(testSubject.contains(testId));

        testSubject.enqueue(testId, generateInitialLetter());

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.contains(otherTestId));
    }

    @Test
    void deadLettersInvocationPerSequenceIdentifierReturnsEnqueuedLettersMatchingGivenSequenceIdentifier() {
        Object testId = generateId();
        DeadLetter<M> expected = generateInitialLetter();

        Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultIterator.hasNext());

        testSubject.enqueue(testId, expected);

        resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultIterator.hasNext());
        assertEquals(expected, resultIterator.next());
        assertFalse(resultIterator.hasNext());
    }

    @Test
    void deadLettersInvocationReturnsAllEnqueuedDeadLetters() {
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
                if (resultLetter.equals(thisFirstExpected)) {
                    assertEquals(thisFirstExpected, resultLetter);
                    assertTrue(resultLetters.hasNext());
                    assertEquals(thisSecondExpected, resultLetters.next());
                    assertFalse(resultLetters.hasNext());
                } else {
                    assertEquals(thatFirstExpected, resultLetter);
                    assertTrue(resultLetters.hasNext());
                    assertEquals(thatSecondExpected, resultLetters.next());
                    assertFalse(resultLetters.hasNext());
                }
            }
        }
    }

    @Test
    void isFullReturnsTrueAfterMaximumAmountOfSequencesIsReached() {
        assertFalse(testSubject.isFull(generateId()));

        long maxSequences = testSubject.maxSequences();
        assertTrue(maxSequences > 0);

        for (int i = 0; i < maxSequences; i++) {
            testSubject.enqueue(generateId(), generateInitialLetter());
        }

        assertTrue(testSubject.isFull(generateId()));
    }

    @Test
    void isFullReturnsTrueAfterMaximumSequenceSizeIsReached() {
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
    void sizeReturnsOverallNumberOfContainedDeadLetters() {
        assertEquals(0, testSubject.size());

        Object testId = generateId();
        testSubject.enqueue(testId, generateInitialLetter());
        assertEquals(1, testSubject.size());
        testSubject.enqueue(testId, generateInitialLetter());
        assertEquals(2, testSubject.size());

        // This generates a new sequence, increasing the size.
        testSubject.enqueue(generateId(), generateInitialLetter());
        assertEquals(3, testSubject.size());
    }

    @Test
    void sequenceSizeForSequenceIdentifierReturnsTheNumberOfContainedLettersForGivenSequenceIdentifier() {
        assertEquals(0, testSubject.sequenceSize("some-id"));

        Object testId = generateId();
        testSubject.enqueue(testId, generateInitialLetter());
        assertEquals(0, testSubject.sequenceSize("some-id"));
        assertEquals(1, testSubject.sequenceSize(testId));
        testSubject.enqueue(testId, generateInitialLetter());
        assertEquals(2, testSubject.sequenceSize(testId));

        // This generates a new sequence, so shouldn't increase the sequence size.
        testSubject.enqueue(generateId(), generateInitialLetter());
        assertEquals(0, testSubject.sequenceSize("some-id"));
        assertEquals(2, testSubject.sequenceSize(testId));
    }

    @Test
    void amountOfSequencesReturnsTheNumberOfUniqueSequences() {
        assertEquals(0, testSubject.amountOfSequences());

        testSubject.enqueue(generateId(), generateInitialLetter());
        assertEquals(1, testSubject.amountOfSequences());

        testSubject.enqueue(generateId(), generateInitialLetter());
        assertEquals(2, testSubject.amountOfSequences());

        Object testId = generateId();
        testSubject.enqueue(testId, generateInitialLetter());
        testSubject.enqueue(testId, generateInitialLetter());
        testSubject.enqueue(testId, generateInitialLetter());
        assertEquals(3, testSubject.amountOfSequences());
    }

    @Test
    void processInvocationReturnsFalseIfThereAreNoLetters() {
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
    void processInvocationReturnsTrueAndEvictsTheLetter() {
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
    void processInvocationReturnsFalseAndRequeuesTheLetter() {
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
    void processInvocationInvokesProcessingTaskInLastTouchedOrderOfLetters() {
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

    @SuppressWarnings("ConstantConditions")
    @Test
    void processInvocationHandlesAllLettersInSequence() {
        AtomicReference<Deque<DeadLetter<? extends M>>> resultLetters = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            Deque<DeadLetter<? extends M>> sequence = resultLetters.get();
            if (sequence == null) {
                sequence = new LinkedList<>();
            }
            sequence.addLast(letter);
            resultLetters.set(sequence);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<? extends M> firstTestLetter = generateInitialLetter();
        testSubject.enqueue(testId, firstTestLetter);
        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now());
        DeadLetter<? extends M> secondTestLetter = generateFollowUpLetter();
        testSubject.enqueueIfPresent(testId, () -> secondTestLetter);
        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now());
        DeadLetter<? extends M> thirdTestLetter = generateFollowUpLetter();
        testSubject.enqueueIfPresent(testId, () -> thirdTestLetter);

        // Add another letter in a different sequence that we do not expect to receive.
        testSubject.enqueue(generateId(), generateInitialLetter());

        boolean result = testSubject.process(testTask);
        assertTrue(result);
        Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

        assertLetter(firstTestLetter, resultSequence.pollFirst());
        assertLetter(secondTestLetter, resultSequence.pollFirst());
        assertLetter(thirdTestLetter, resultSequence.pollFirst());
    }

    /**
     * A "claimed sequence" in this case means that a process task for a given "sequence identifier" is still processing
     * the sequence. Furthermore, if it's the sole sequence, the processing task will not be invoked. This approach
     * ensure the events for a given sequence identifier are handled in the order they've been dead-lettered (a.k.a., in
     * sequence).
     */
    @Test
    void processInvocationReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
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
        assertTrue(isBlocking.await(10, TimeUnit.MILLISECONDS));

        boolean result = testSubject.process(nonBlockingTask);
        assertFalse(result);
        assertFalse(invoked.get());

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void processWithLetterPredicateReturnsFalseIfThereAreNoMatchingLetters() {
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
    void processWithLetterPredicateInvokesProcessingTaskWithMatchingLetter() {
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

        boolean result = testSubject.process(letter -> letter.message().equals(testThisLetter.message()), testTask);
        assertTrue(result);
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(letter -> letter.message().equals(testThatLetter.message()), testTask);
        assertTrue(result);
        assertLetter(testThatLetter, resultLetter.get());
    }

    @Test
    void processWithLetterPredicateReturnsTrueAndEvictsTheLetter() {
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

        boolean result = testSubject.process(letter -> letter.message().equals(testLetter.message()), testTask);
        assertTrue(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultLetters.hasNext());
        assertTrue(testSubject.deadLetters().iterator().hasNext());
    }

    @Test
    void processWithLetterPredicateReturnsFalseAndRequeuesTheLetter() {
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

        boolean result = testSubject.process(letter -> letter.message().equals(testLetter.message()), testTask);
        assertFalse(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
        assertTrue(testSubject.deadLetters().iterator().hasNext());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void processWithLetterPredicateHandlesAllLettersInSequence() {
        AtomicReference<Deque<DeadLetter<? extends M>>> resultLetters = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            Deque<DeadLetter<? extends M>> sequence = resultLetters.get();
            if (sequence == null) {
                sequence = new LinkedList<>();
            }
            sequence.addLast(letter);
            resultLetters.set(sequence);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<? extends M> firstTestLetter = generateInitialLetter();
        testSubject.enqueue(testId, firstTestLetter);
        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now());
        DeadLetter<? extends M> secondTestLetter = generateFollowUpLetter();
        testSubject.enqueueIfPresent(testId, () -> secondTestLetter);
        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now());
        DeadLetter<? extends M> thirdTestLetter = generateFollowUpLetter();
        testSubject.enqueueIfPresent(testId, () -> thirdTestLetter);

        // Add another letter in a different sequence that we do not expect to receive.
        testSubject.enqueue(generateId(), generateInitialLetter());

        boolean result = testSubject.process(letter -> letter.equals(firstTestLetter), testTask);
        assertTrue(result);
        Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

        assertLetter(firstTestLetter, resultSequence.pollFirst());
        assertLetter(secondTestLetter, resultSequence.pollFirst());
        assertLetter(thirdTestLetter, resultSequence.pollFirst());
    }

    /**
     * A "claimed sequence" in this case means that a process task for a given "sequence identifier" is still processing
     * the sequence. Furthermore, if it's the sole sequence, the processing task will not be invoked. This approach
     * ensure the events for a given sequence identifier are handled in the order they've been dead-lettered (a.k.a., in
     * sequence).
     */
    @Test
    void processWithLetterPredicateReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
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
                letter -> letter.message().equals(testLetter.message()),
                blockingTask
        ));
        blockingProcess.start();
        assertTrue(isBlocking.await(10, TimeUnit.MILLISECONDS));

        boolean result = testSubject.process(letter -> letter.message().equals(testLetter.message()), nonBlockingTask);
        assertFalse(result);
        assertFalse(invoked.get());

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void clearInvocationRemovesAllEntries() {
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
     * Generate a unique {@link Object} based on {@link UUID#randomUUID()}.
     *
     * @return A unique {@link Object}, based on {@link UUID#randomUUID()}.
     */
    protected static Object generateId() {
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
    abstract DeadLetter<M> generateInitialLetter();

    /**
     * Generate a follow-up {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}. This means the dead-letter is part of a sequence of letters.
     *
     * @return A follow-up {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}.
     */
    abstract DeadLetter<M> generateFollowUpLetter();

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
    abstract DeadLetter<M> generateRequeuedLetter(DeadLetter<M> original,
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
    abstract void setClock(Clock clock);

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
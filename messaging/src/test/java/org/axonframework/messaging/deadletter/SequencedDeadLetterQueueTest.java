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
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;
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
 * @param <I> The {@link SequenceIdentifier} implementation used to enqueue letters by this test class.
 * @param <D> The {@link DeadLetter} implementation enqueued by this test class.
 * @author Steven van Beelen
 */
public abstract class SequencedDeadLetterQueueTest<I extends SequenceIdentifier, D extends DeadLetter<?>> {

    private SequencedDeadLetterQueue<D> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = buildTestSubject();
    }

    /**
     * Constructs the {@link SequencedDeadLetterQueue} implementation under test.
     *
     * @return A {@link SequencedDeadLetterQueue} implementation under test.
     */
    abstract SequencedDeadLetterQueue<D> buildTestSubject();

    @Test
    void testEnqueue() {
        D testLetter = generateInitialLetter();

        testSubject.enqueue(testLetter);

        assertTrue(testSubject.contains(testLetter.sequenceIdentifier()));
        Iterator<D> resultLetters = testSubject.deadLetterSequence(testLetter.sequenceIdentifier()).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequencesIsReached() {
        long maxSequences = testSubject.maxSequences();
        assertTrue(maxSequences > 0);

        for (int i = 0; i < maxSequences; i++) {
            testSubject.enqueue(generateInitialLetter());
        }

        assertThrows(DeadLetterQueueOverflowException.class, () -> testSubject.enqueue(generateInitialLetter()));
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequenceSizeIsReached() {
        I testId = generateSequenceId();

        long maxSequenceSize = testSubject.maxSequenceSize();
        assertTrue(maxSequenceSize > 0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(generateInitialLetter(testId));
        }

        assertThrows(DeadLetterQueueOverflowException.class, () -> testSubject.enqueue(generateInitialLetter(testId)));
    }

    @Test
    void testEnqueueIfPresentThrowsDeadLetterQueueOverflowExceptionForFullQueue() {
        I testId = generateSequenceId();

        long maxSequenceSize = testSubject.maxSequenceSize();
        assertTrue(maxSequenceSize > 0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(generateInitialLetter(testId));
        }

        //noinspection unchecked
        assertThrows(DeadLetterQueueOverflowException.class,
                     () -> testSubject.enqueueIfPresent(testId, id -> generateFollowUpLetter((I) id)));
    }

    @Test
    void testEnqueueIfPresentThrowsMismatchingSequenceIdentifierExceptionWhenTheGivenIdDoesNotMatchTheLetterId() {
        I testId = generateSequenceId();
        I otherTestId = generateSequenceId();

        testSubject.enqueue(generateInitialLetter(testId));

        assertThrows(MismatchingSequenceIdentifierException.class,
                     () -> testSubject.enqueueIfPresent(testId, id -> generateFollowUpLetter(otherTestId)));
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForEmptyQueue() {
        I testId = generateSequenceId();

        //noinspection unchecked
        boolean result = testSubject.enqueueIfPresent(testId, id -> generateFollowUpLetter((I) id));

        assertFalse(result);
        assertFalse(testSubject.contains(testId));
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForNonExistentSequenceIdentifier() {
        I testFirstId = generateSequenceId();

        testSubject.enqueue(generateInitialLetter(testFirstId));

        I testSecondId = generateSequenceId();

        //noinspection unchecked
        boolean result = testSubject.enqueueIfPresent(testSecondId, id -> generateFollowUpLetter((I) id));

        assertFalse(result);
        assertTrue(testSubject.contains(testFirstId));
        assertFalse(testSubject.contains(testSecondId));
    }

    @Test
    void testEnqueueIfPresentEnqueuesForExistingSequenceIdentifier() {
        I testId = generateSequenceId();
        D testFirstLetter = generateInitialLetter(testId);
        D testSecondLetter = generateFollowUpLetter(testId);

        testSubject.enqueue(testFirstLetter);
        testSubject.enqueueIfPresent(testId, id -> testSecondLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(testFirstLetter, resultLetters.next());
        assertTrue(resultLetters.hasNext());
        assertLetter(testSecondLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testEvictDoesNotChangeTheQueueForNonExistentSequenceIdentifier() {
        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
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
    void testEvictDoesNotChangeTheQueueForNonExistentLetterIdentifier() {
        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertEquals(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());

        testSubject.evict(generateInitialLetter(testId));

        assertTrue(testSubject.contains(testId));
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertEquals(testLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testEvictRemovesLetterFromQueue() {
        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);

        testSubject.enqueue(testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        D resultLetter = resultLetters.next();
        assertLetter(testLetter, resultLetter);
        assertFalse(resultLetters.hasNext());

        testSubject.evict(resultLetter);

        assertFalse(testSubject.contains(testId));
        assertTrue(testSubject.deadLetters().isEmpty());
    }

    @Test
    void testRequeueThrowsNoSuchDeadLetterExceptionForNonExistentSequenceIdentifier() {
        assertThrows(NoSuchDeadLetterException.class,
                     () -> testSubject.requeue(generateInitialLetter(), generateThrowable()));
    }

    @Test
    void testRequeueThrowsNoSuchDeadLetterExceptionForNonExistentLetterIdentifier() {
        I testId = generateSequenceId();
        testSubject.enqueue(generateInitialLetter(testId));

        assertThrows(NoSuchDeadLetterException.class,
                     () -> testSubject.requeue(generateInitialLetter(testId), generateThrowable()));
    }

    @Test
    void testRequeueReenterLetterToQueueWithUpdatedLastTouchedAndCause() {
        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        Throwable testThrowable = generateThrowable();

        testSubject.enqueue(testLetter);

        assertTrue(testSubject.contains(testId));
        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        D resultLetter = resultLetters.next();
        assertLetter(testLetter, resultLetter);
        assertFalse(resultLetters.hasNext());

        D expectedLetter = generateRequeuedLetter(testLetter, testThrowable);

        testSubject.requeue(resultLetter, testThrowable);

        assertTrue(testSubject.contains(testId));
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testContains() {
        I testId = generateSequenceId();
        I otherTestId = generateSequenceId();
        I otherTestIdWithSameGroup = generateSequenceId(testId.group());

        assertFalse(testSubject.contains(testId));

        testSubject.enqueue(generateInitialLetter(testId));

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.contains(otherTestId));
        assertFalse(testSubject.contains(otherTestIdWithSameGroup));
    }

    @Test
    void testDeadLettersPerSequenceIdentifier() {
        I testId = generateSequenceId();
        D expected = generateInitialLetter(testId);

        Iterator<D> resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultIterator.hasNext());

        testSubject.enqueue(expected);

        resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultIterator.hasNext());
        assertEquals(expected, resultIterator.next());
        assertFalse(resultIterator.hasNext());
    }

    @Test
    void testDeadLetters() {
        I thisTestId = generateSequenceId("first");
        I thatTestId = generateSequenceId("second");

        Map<SequenceIdentifier, Iterable<D>> result = testSubject.deadLetters();
        assertTrue(result.isEmpty());

        D thisFirstExpected = generateInitialLetter(thisTestId);
        D thisSecondExpected = generateInitialLetter(thisTestId);
        D thatFirstExpected = generateInitialLetter(thatTestId);
        D thatSecondExpected = generateInitialLetter(thatTestId);

        testSubject.enqueue(thisFirstExpected);
        testSubject.enqueue(thatFirstExpected);
        testSubject.enqueue(thisSecondExpected);
        testSubject.enqueue(thatSecondExpected);

        result = testSubject.deadLetters();
        assertFalse(result.isEmpty());

        assertTrue(result.containsKey(thisTestId));
        Iterator<D> thisResultLetters = result.get(thisTestId).iterator();
        assertTrue(thisResultLetters.hasNext());
        assertEquals(thisFirstExpected, thisResultLetters.next());
        assertTrue(thisResultLetters.hasNext());
        assertEquals(thisSecondExpected, thisResultLetters.next());
        assertFalse(thisResultLetters.hasNext());

        assertTrue(result.containsKey(thatTestId));
        Iterator<D> thatResultLetters = result.get(thatTestId).iterator();
        assertTrue(thatResultLetters.hasNext());
        assertEquals(thatFirstExpected, thatResultLetters.next());
        assertTrue(thatResultLetters.hasNext());
        assertEquals(thatSecondExpected, thatResultLetters.next());
        assertFalse(thatResultLetters.hasNext());
    }

    @Test
    void testIsFullReturnsTrueAfterMaximumAmountOfSequencesIsReached() {
        assertFalse(testSubject.isFull(generateSequenceId()));

        long maxSequences = testSubject.maxSequences();
        assertTrue(maxSequences > 0);

        for (int i = 0; i < maxSequences; i++) {
            testSubject.enqueue(generateInitialLetter());
        }

        assertTrue(testSubject.isFull(generateSequenceId()));
    }

    @Test
    void testIsFullReturnsTrueAfterMaximumSequenceSizeIsReached() {
        I testId = generateSequenceId();

        assertFalse(testSubject.isFull(testId));

        long maxSequenceSize = testSubject.maxSequenceSize();
        assertTrue(maxSequenceSize > 0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(generateInitialLetter(testId));
        }

        assertTrue(testSubject.isFull(testId));
    }

    @Test
    void testProcessReturnsFalseIfThereAreNoLetters() {
        AtomicBoolean taskInvoked = new AtomicBoolean(false);
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            taskInvoked.set(true);
            return Decisions.evict();
        };

        boolean result = testSubject.process(testTask);

        assertFalse(result);
        assertFalse(taskInvoked.get());
    }


    @Test
    void testProcessReturnsTrueAndEvictsTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);

        boolean result = testSubject.process(testTask);
        assertTrue(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testProcessReturnsFalseAndRequeuesTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);

        Instant expectedLastTouched = setAndGetTime();
        D expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean result = testSubject.process(testTask);
        assertFalse(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
    }

    @Test
    void testProcessInvokesProcessingTaskWithLastTouchedOrder() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        I testThisId = generateSequenceId();
        D testThisLetter = generateInitialLetter(testThisId);
        testSubject.enqueue(testThisLetter);

        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime();
        I testThatId = generateSequenceId();
        D testThatLetter = generateInitialLetter(testThatId);
        testSubject.enqueue(testThatLetter);

        boolean result = testSubject.process(testTask);
        assertTrue(result);
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(testTask);
        assertTrue(result);
        assertLetter(testThatLetter, resultLetter.get());
    }

    /**
     * A "claimed sequence" in this case means that a letter with {@link SequenceIdentifier} {@code x} is not
     * {@link SequencedDeadLetterQueue#evict(DeadLetter)  evicted} or
     * {@link SequencedDeadLetterQueue#requeue(DeadLetter, Throwable) requeued} yet. Furthermore, if it's the sole
     * letter, nothing should be returned. This approach ensure the events for a given {@link SequenceIdentifier} are
     * handled in the order they've been dead-lettered (a.k.a., in sequence).
     */
    @Test
    void testProcessReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasProcessed = new CountDownLatch(1);
        AtomicReference<D> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<D, EnqueueDecision<D>> blockingTask = letter -> {
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
        Function<D, EnqueueDecision<D>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);

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
    void testProcessForGroupReturnsFalseIfThereAreNoMatchingLetters() {
        AtomicBoolean releasedLetter = new AtomicBoolean(false);
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            releasedLetter.set(true);
            return Decisions.evict();
        };

        I testThisId = generateSequenceId();
        testSubject.enqueue(generateInitialLetter(testThisId));

        I testThatId = generateSequenceId();
        testSubject.enqueue(generateInitialLetter(testThatId));

        boolean result = testSubject.process("non-matching-group", testTask);
        assertFalse(result);
        assertFalse(releasedLetter.get());
    }

    @Test
    void testProcessForGroupInvokesProcessingTaskWithGroupMatchingLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        I testThisId = generateSequenceId();
        D testThisLetter = generateInitialLetter(testThisId);
        testSubject.enqueue(testThisLetter);

        I testThatId = generateSequenceId();
        D testThatLetter = generateInitialLetter(testThatId);
        testSubject.enqueue(testThatLetter);

        boolean result = testSubject.process(testThisId.group(), testTask);
        assertTrue(result);
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(testThatId.group(), testTask);
        assertTrue(result);
        assertLetter(testThatLetter, resultLetter.get());
    }

    @Test
    void testProcessForGroupReturnsTrueAndEvictsTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-group-matching letter
        testSubject.enqueue(generateInitialLetter(generateSequenceId("non-matching-group")));

        boolean result = testSubject.process(testId.group(), testTask);
        assertTrue(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultLetters.hasNext());
        assertFalse(testSubject.deadLetters().isEmpty());
    }

    @Test
    void testProcessForGroupReturnsFalseAndRequeuesTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-group-matching letter
        testSubject.enqueue(generateInitialLetter(generateSequenceId("non-matching-group")));

        Instant expectedLastTouched = setAndGetTime();
        D expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean result = testSubject.process(testId.group(), testTask);
        assertFalse(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
        assertFalse(testSubject.deadLetters().isEmpty());
    }

    /**
     * A "claimed sequence" in this case means that a letter with {@link SequenceIdentifier} {@code x} is not
     * {@link SequencedDeadLetterQueue#evict(DeadLetter) evicted} or
     * {@link SequencedDeadLetterQueue#requeue(DeadLetter, Throwable) requeued} yet. Furthermore, if it's the sole
     * letter for that {@code group}, nothing should be returned. This approach ensure the events for a given
     * {@link SequenceIdentifier} are handled in the order they've been dead-lettered (a.k.a., in sequence).
     */
    @Test
    void testProcessForGroupReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasProcessed = new CountDownLatch(1);
        AtomicReference<D> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<D, EnqueueDecision<D>> blockingTask = letter -> {
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
        Function<D, EnqueueDecision<D>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        I testId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-group-matching letter
        testSubject.enqueue(generateInitialLetter(generateSequenceId("non-matching-group")));

        Thread blockingProcess = new Thread(() -> testSubject.process(testId.group(), blockingTask));
        blockingProcess.start();
        assertTrue(isBlocking.await(10, TimeUnit.MILLISECONDS));

        boolean result = testSubject.process(testId.group(), nonBlockingTask);
        assertFalse(result);
        assertFalse(invoked.get());

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void testProcessForIdPredicateReturnsFalseIfThereAreNoMatchingLetters() {
        AtomicBoolean releasedLetter = new AtomicBoolean(false);
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            releasedLetter.set(true);
            return Decisions.evict();
        };

        I testThisId = generateSequenceId();
        testSubject.enqueue(generateInitialLetter(testThisId));

        I testThatId = generateSequenceId();
        testSubject.enqueue(generateInitialLetter(testThatId));

        boolean result = testSubject.process(sequenceId -> sequenceId.equals(generateSequenceId()), testTask);
        assertFalse(result);
        assertFalse(releasedLetter.get());
    }

    @Test
    void testProcessForIdPredicateInvokesProcessingTaskWithMatchingLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        I testThisId = generateSequenceId();
        D testThisLetter = generateInitialLetter(testThisId);
        testSubject.enqueue(testThisLetter);

        I testThatId = generateSequenceId();
        D testThatLetter = generateInitialLetter(testThatId);
        testSubject.enqueue(testThatLetter);

        boolean result = testSubject.process(sequenceId -> sequenceId.equals(testThisId), testTask);
        assertTrue(result);
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(sequenceId -> sequenceId.equals(testThatId), testTask);
        assertTrue(result);
        assertLetter(testThatLetter, resultLetter.get());
    }

    @Test
    void testProcessForIdPredicateReturnsTrueAndEvictsTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        I testId = generateSequenceId();
        I nonMatchingId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(generateInitialLetter(nonMatchingId));

        boolean result = testSubject.process(sequenceId -> sequenceId.equals(testId), testTask);
        assertTrue(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultLetters.hasNext());
        assertFalse(testSubject.deadLetters().isEmpty());
    }

    @Test
    void testProcessForIdPredicateReturnsFalseAndRequeuesTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        I testId = generateSequenceId();
        I nonMatchingId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(generateInitialLetter(nonMatchingId));

        Instant expectedLastTouched = setAndGetTime();
        D expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean result = testSubject.process(sequenceId -> sequenceId.equals(testId), testTask);
        assertFalse(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
        assertFalse(testSubject.deadLetters().isEmpty());
    }

    /**
     * A "claimed sequence" in this case means that a letter with {@link SequenceIdentifier} {@code x} is not
     * {@link SequencedDeadLetterQueue#evict(DeadLetter) evicted} or
     * {@link SequencedDeadLetterQueue#requeue(DeadLetter, Throwable) requeued} yet. Furthermore, if it's the sole
     * letter matching that {@link java.util.function.Predicate}, nothing should be returned. This approach ensure the
     * events for a given {@link SequenceIdentifier} are handled in the order they've been dead-lettered (a.k.a., in
     * sequence).
     */
    @Test
    void testProcessForIdPredicateReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasProcessed = new CountDownLatch(1);
        AtomicReference<D> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<D, EnqueueDecision<D>> blockingTask = letter -> {
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
        Function<D, EnqueueDecision<D>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        I testId = generateSequenceId();
        I nonMatchingId = generateSequenceId();
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(generateInitialLetter(nonMatchingId));

        Thread blockingProcess =
                new Thread(() -> testSubject.process(sequenceId -> sequenceId.equals(testId), blockingTask));
        blockingProcess.start();
        assertTrue(isBlocking.await(10, TimeUnit.MILLISECONDS));

        boolean result = testSubject.process(sequenceId -> sequenceId.equals(testId), nonBlockingTask);
        assertFalse(result);
        assertFalse(invoked.get());

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void testProcessForIdAndLetterPredicateReturnsFalseIfThereAreNoMatchingLetters() {
        AtomicBoolean releasedLetter = new AtomicBoolean(false);
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            releasedLetter.set(true);
            return Decisions.evict();
        };

        String testGroup = "test-group";
        I testThisId = generateSequenceId(testGroup);
        D testThisLetter = generateInitialLetter(testThisId);
        testSubject.enqueue(testThisLetter);

        I testThatId = generateSequenceId(testGroup);
        testSubject.enqueue(generateInitialLetter(testThatId));

        boolean result = testSubject.process(sequenceId -> sequenceId.group().equals(testGroup),
                                             letter -> letter.identifier().equals("no-matching-id"),
                                             testTask);
        assertFalse(result);
        assertFalse(releasedLetter.get());
    }

    @Test
    void testProcessForIdAndLetterPredicateInvokesProcessingTaskWithMatchingLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        String testGroup = "test-group";
        I testThisId = generateSequenceId(testGroup);
        D testThisLetter = generateInitialLetter(testThisId);
        testSubject.enqueue(testThisLetter);

        I testThatId = generateSequenceId(testGroup);
        D testThatLetter = generateInitialLetter(testThatId);
        testSubject.enqueue(testThatLetter);

        boolean result = testSubject.process(sequenceId -> sequenceId.group().equals(testGroup),
                                             letter -> letter.message().equals(testThisLetter.message()),
                                             testTask);
        assertTrue(result);
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(sequenceId -> sequenceId.group().equals(testGroup),
                                     letter -> letter.message().equals(testThatLetter.message()),
                                     testTask);
        assertTrue(result);
        assertLetter(testThatLetter, resultLetter.get());
    }

    @Test
    void testProcessForIdAndLetterPredicateReturnsTrueAndEvictsTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        String testGroup = "test-group";
        I testId = generateSequenceId(testGroup);
        I nonMatchingId = generateSequenceId(testGroup);
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(generateInitialLetter(nonMatchingId));

        boolean result = testSubject.process(sequenceId -> sequenceId.group().equals(testGroup),
                                             letter -> letter.message().equals(testLetter.message()),
                                             testTask);
        assertTrue(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertFalse(resultLetters.hasNext());
        assertFalse(testSubject.deadLetters().isEmpty());
    }

    @Test
    void testProcessForIdAndLetterPredicateReturnsFalseAndRequeuesTheLetter() {
        AtomicReference<D> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<D, EnqueueDecision<D>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        String testGroup = "test-group";
        I testId = generateSequenceId(testGroup);
        I nonMatchingId = generateSequenceId(testGroup);
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(generateInitialLetter(nonMatchingId));

        Instant expectedLastTouched = setAndGetTime();
        D expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean result = testSubject.process(sequenceId -> sequenceId.group().equals(testGroup),
                                             letter -> letter.message().equals(testLetter.message()),
                                             testTask);
        assertFalse(result);
        assertLetter(testLetter, resultLetter.get());

        Iterator<D> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertTrue(resultLetters.hasNext());
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertFalse(resultLetters.hasNext());
        assertFalse(testSubject.deadLetters().isEmpty());
    }

    /**
     * A "claimed sequence" in this case means that a letter with {@link SequenceIdentifier} {@code x} is not
     * {@link SequencedDeadLetterQueue#evict(DeadLetter) evicted} or
     * {@link SequencedDeadLetterQueue#requeue(DeadLetter, Throwable) requeued} yet. Furthermore, if it's the sole
     * letter matching that {@link java.util.function.Predicate}, nothing should be returned. This approach ensure the
     * events for a given {@link SequenceIdentifier} are handled in the order they've been dead-lettered (a.k.a., in
     * sequence).
     */
    @Test
    void testProcessForIdAndLetterPredicateReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasProcessed = new CountDownLatch(1);
        AtomicReference<D> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<D, EnqueueDecision<D>> blockingTask = letter -> {
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
        Function<D, EnqueueDecision<D>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        String testGroup = "test-group";
        I testId = generateSequenceId(testGroup);
        I nonMatchingId = generateSequenceId(testGroup);
        D testLetter = generateInitialLetter(testId);
        testSubject.enqueue(testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(generateInitialLetter(nonMatchingId));

        Thread blockingProcess = new Thread(() -> testSubject.process(
                sequenceId -> sequenceId.group().equals(testGroup),
                letter -> letter.message().equals(testLetter.message()),
                blockingTask
        ));
        blockingProcess.start();
        assertTrue(isBlocking.await(10, TimeUnit.MILLISECONDS));

        boolean result = testSubject.process(sequenceId -> sequenceId.group().equals(testGroup),
                                             letter -> letter.message().equals(testLetter.message()),
                                             nonBlockingTask);
        assertFalse(result);
        assertFalse(invoked.get());

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void testClearRemovesAllEntries() {
        I idOne = generateSequenceId();
        I idTwo = generateSequenceId();
        I idThree = generateSequenceId();

        assertFalse(testSubject.contains(idOne));
        assertFalse(testSubject.contains(idTwo));
        assertFalse(testSubject.contains(idThree));

        testSubject.enqueue(generateInitialLetter(idOne));
        testSubject.enqueue(generateInitialLetter(idTwo));
        testSubject.enqueue(generateInitialLetter(idThree));

        assertTrue(testSubject.contains(idOne));
        assertTrue(testSubject.contains(idTwo));
        assertTrue(testSubject.contains(idThree));

        testSubject.clear();

        assertFalse(testSubject.contains(idOne));
        assertFalse(testSubject.contains(idTwo));
        assertFalse(testSubject.contains(idThree));
    }

    @Test
    void testClearForGroupRemovesAllEntriesOfThatGroup() {
        I thisId = generateSequenceId();
        I thatId = generateSequenceId();

        assertFalse(testSubject.contains(thisId));
        assertFalse(testSubject.contains(thatId));

        testSubject.enqueue(generateInitialLetter(thisId));
        testSubject.enqueue(generateInitialLetter(thisId));
        testSubject.enqueue(generateInitialLetter(thatId));
        testSubject.enqueue(generateInitialLetter(thatId));

        assertTrue(testSubject.contains(thisId));
        assertTrue(testSubject.contains(thatId));

        testSubject.clear(thisId.group());

        assertFalse(testSubject.contains(thisId));
        assertTrue(testSubject.contains(thatId));
    }

    @Test
    void testClearWithPredicateRemovesAllMatchingEntries() {
        I thisId = generateSequenceId();
        I thatId = generateSequenceId();
        I thirdId = generateSequenceId();

        assertFalse(testSubject.contains(thisId));
        assertFalse(testSubject.contains(thatId));
        assertFalse(testSubject.contains(thirdId));

        testSubject.enqueue(generateInitialLetter(thisId));
        testSubject.enqueue(generateInitialLetter(thisId));
        testSubject.enqueue(generateInitialLetter(thatId));
        testSubject.enqueue(generateInitialLetter(thatId));
        testSubject.enqueue(generateInitialLetter(thirdId));
        testSubject.enqueue(generateInitialLetter(thirdId));

        assertTrue(testSubject.contains(thisId));
        assertTrue(testSubject.contains(thatId));
        assertTrue(testSubject.contains(thirdId));

        testSubject.clear(id -> id.equals(thisId) || id.equals(thirdId));

        assertFalse(testSubject.contains(thisId));
        assertTrue(testSubject.contains(thatId));
        assertFalse(testSubject.contains(thirdId));
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
     * Constructs a {@link SequenceIdentifier} implementation expected by the test subject.
     *
     * @return A {@link SequenceIdentifier} implementation expected by the test subject.
     */
    abstract I generateSequenceId();

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
    protected D generateInitialLetter() {
        return generateInitialLetter(generateSequenceId());
    }

    /**
     * Generate an initial {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}. This means the dead-letter is the first in a potential sequence of letters.
     *
     * @return An initial {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}
     */
    abstract D generateInitialLetter(I sequenceIdentifier);

    /**
     * Generate a follow-up {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}. This means the dead-letter is part of a sequence of letters.
     *
     * @return A follow-up {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}.
     */
    abstract D generateFollowUpLetter(I sequenceIdentifier);

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
    protected D generateRequeuedLetter(D original, Throwable requeueCause) {
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
    abstract D generateRequeuedLetter(D original, Instant lastTouched, Throwable requeueCause, MetaData diagnostics);

    /**
     * Constructs a {@link SequenceIdentifier} implementation expected by the test subject.
     *
     * @return A {@link SequenceIdentifier} implementation expected by the test subject.
     */
    abstract I generateSequenceId(String group);

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
    protected void assertLetter(D expected, D actual) {
        assertEquals(expected.sequenceIdentifier(), actual.sequenceIdentifier());
        assertEquals(expected.message(), actual.message());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(expected.enqueuedAt(), actual.enqueuedAt());
        assertEquals(expected.lastTouched(), actual.lastTouched());
        assertEquals(expected.diagnostic(), actual.diagnostic());
    }
}
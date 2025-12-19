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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract class providing a generic test suite every {@link SequencedDeadLetterQueue} implementation should comply
 * with.
 * <p>
 * All tests are adapted for the AF5 async API that returns {@link CompletableFuture}.
 *
 * @param <M> The {@link DeadLetter} implementation enqueued by this test class.
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public abstract class SequencedDeadLetterQueueTest<M extends Message> {

    private SequencedDeadLetterQueue<M> testSubject;

    @BeforeEach
    void setUp() {
        // Reset clock to current time to avoid timestamp issues from previous tests
        setClock(Clock.systemDefaultZone());
        testSubject = buildTestSubject();
    }

    /**
     * Constructs the {@link SequencedDeadLetterQueue} implementation under test.
     *
     * @return A {@link SequencedDeadLetterQueue} implementation under test.
     */
    protected abstract SequencedDeadLetterQueue<M> buildTestSubject();

    /**
     * Return the configured maximum amount of sequences for the {@link #buildTestSubject() test subject}.
     *
     * @return The configured maximum amount of sequences for the {@link #buildTestSubject() test subject}.
     */
    protected abstract long maxSequences();

    /**
     * Return the configured maximum size of a sequence for the {@link #buildTestSubject() test subject}.
     *
     * @return The configured maximum size of a sequence for the {@link #buildTestSubject() test subject}.
     */
    protected abstract long maxSequenceSize();

    @Nested
    class WhenEnqueueing {

        @Test
        void enqueueAddsDeadLetter() {
            // given
            Object testId = generateId();
            DeadLetter<? extends M> testLetter = generateInitialLetter();

            // when
            testSubject.enqueue(testId, testLetter).join();

            // then
            assertTrue(testSubject.contains(testId).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(testLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequencesIsReached() {
            // given
            long maxSequences = maxSequences();
            assertTrue(maxSequences > 0);
            for (int i = 0; i < maxSequences; i++) {
                testSubject.enqueue(generateId(), generateInitialLetter()).join();
            }

            // when / then
            Object oneSequenceToMany = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            assertThatThrownBy(() -> testSubject.enqueue(oneSequenceToMany, testLetter).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(DeadLetterQueueOverflowException.class);
        }

        @Test
        void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequenceSizeIsReached() {
            // given
            Object testId = generateId();
            long maxSequenceSize = maxSequenceSize();
            assertTrue(maxSequenceSize > 0);
            for (int i = 0; i < maxSequenceSize; i++) {
                testSubject.enqueue(testId, generateInitialLetter()).join();
            }

            // when / then
            DeadLetter<M> oneLetterToMany = generateInitialLetter();
            assertThatThrownBy(() -> testSubject.enqueue(testId, oneLetterToMany).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(DeadLetterQueueOverflowException.class);
        }
    }

    @Nested
    class WhenEnqueueingIfPresent {

        @Test
        void enqueueIfPresentThrowsDeadLetterQueueOverflowExceptionForFullQueue() {
            // given
            Object testId = generateId();
            long maxSequenceSize = maxSequenceSize();
            assertTrue(maxSequenceSize > 0);
            for (int i = 0; i < maxSequenceSize; i++) {
                testSubject.enqueue(testId, generateInitialLetter()).join();
            }

            // when / then
            assertThatThrownBy(() -> testSubject.enqueueIfPresent(testId, SequencedDeadLetterQueueTest.this::generateFollowUpLetter).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(DeadLetterQueueOverflowException.class);
        }

        @Test
        void enqueueIfPresentDoesNotEnqueueForEmptyQueue() {
            // given
            Object testId = generateId();

            // when
            boolean result = testSubject.enqueueIfPresent(testId, SequencedDeadLetterQueueTest.this::generateFollowUpLetter).join();

            // then
            assertFalse(result);
            assertFalse(testSubject.contains(testId).join());
        }

        @Test
        void enqueueIfPresentDoesNotEnqueueForNonExistentSequenceIdentifier() {
            // given
            Object testFirstId = generateId();
            testSubject.enqueue(testFirstId, generateInitialLetter()).join();
            Object testSecondId = generateId();

            // when
            boolean result = testSubject.enqueueIfPresent(testSecondId, SequencedDeadLetterQueueTest.this::generateFollowUpLetter).join();

            // then
            assertFalse(result);
            assertTrue(testSubject.contains(testFirstId).join());
            assertFalse(testSubject.contains(testSecondId).join());
        }

        @Test
        void enqueueIfPresentEnqueuesForExistingSequenceIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<M> testFirstLetter = generateInitialLetter();
            DeadLetter<M> testSecondLetter = generateFollowUpLetter();

            // when
            testSubject.enqueue(testId, testFirstLetter).join();
            testSubject.enqueueIfPresent(testId, () -> testSecondLetter).join();

            // then
            assertTrue(testSubject.contains(testId).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(testFirstLetter, resultLetters.next());
            assertTrue(resultLetters.hasNext());
            assertLetter(testSecondLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }
    }

    @Nested
    class WhenEvicting {

        @Test
        void evictDoesNotChangeTheQueueForNonExistentSequenceIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<? extends M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter).join();

            // when
            testSubject.evict(mapToQueueImplementation(generateInitialLetter())).join();

            // then
            assertTrue(testSubject.contains(testId).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(testLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void evictDoesNotChangeTheQueueForNonExistentLetterIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter).join();

            // when
            testSubject.evict(mapToQueueImplementation(generateInitialLetter())).join();

            // then
            assertTrue(testSubject.contains(testId).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(testLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void evictRemovesLetterFromQueue() {
            // given
            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter).join();
            DeadLetter<? extends M> resultLetter = testSubject.deadLetterSequence(testId).join().iterator().next();

            // when
            testSubject.evict(resultLetter).join();

            // then
            assertFalse(testSubject.contains(testId).join());
            assertFalse(testSubject.deadLetters().join().iterator().hasNext());
        }
    }

    @Nested
    class WhenRequeueing {

        @Test
        void requeueThrowsNoSuchDeadLetterExceptionForNonExistentSequenceIdentifier() {
            // given
            DeadLetter<M> testLetter = generateInitialLetter();

            // when / then
            assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(testLetter), l -> l).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(NoSuchDeadLetterException.class);
        }

        @Test
        void requeueThrowsNoSuchDeadLetterExceptionForNonExistentLetterIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            DeadLetter<M> otherTestLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter).join();

            // when / then
            assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(otherTestLetter), l -> l).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(NoSuchDeadLetterException.class);
        }

        @Test
        void requeueReentersLetterToQueueWithUpdatedLastTouchedAndCause() {
            // given
            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            Throwable testCause = generateThrowable();
            testSubject.enqueue(testId, testLetter).join();
            DeadLetter<? extends M> resultLetter = testSubject.deadLetterSequence(testId).join().iterator().next();
            DeadLetter<M> expectedLetter = generateRequeuedLetter(testLetter, testCause);

            // when
            testSubject.requeue(resultLetter, l -> l.withCause(testCause)).join();

            // then
            assertTrue(testSubject.contains(testId).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(expectedLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }
    }

    @Nested
    class WhenQuerying {

        @Test
        void containsReturnsTrueForContainedLetter() {
            // given
            Object testId = generateId();
            Object otherTestId = generateId();

            // when / then
            assertFalse(testSubject.contains(testId).join());
            testSubject.enqueue(testId, generateInitialLetter()).join();
            assertTrue(testSubject.contains(testId).join());
            assertFalse(testSubject.contains(otherTestId).join());
        }

        @Test
        void deadLetterSequenceReturnsEnqueuedLettersMatchingGivenSequenceIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<M> expected = generateInitialLetter();

            // when / then
            Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId).join().iterator();
            assertFalse(resultIterator.hasNext());

            testSubject.enqueue(testId, expected).join();

            resultIterator = testSubject.deadLetterSequence(testId).join().iterator();
            assertTrue(resultIterator.hasNext());
            assertLetter(expected, resultIterator.next());
            assertFalse(resultIterator.hasNext());
        }

        @Test
        void deadLetterSequenceReturnsMatchingEnqueuedLettersInInsertOrder() {
            // given
            Object testId = generateId();
            LinkedHashMap<Integer, DeadLetter<M>> enqueuedLetters = new LinkedHashMap<>();
            DeadLetter<M> initial = generateInitialLetter();
            testSubject.enqueue(testId, initial).join();
            enqueuedLetters.put(0, initial);

            IntStream.range(1, Long.valueOf(maxSequenceSize()).intValue())
                     .forEach(i -> {
                         DeadLetter<M> followUp = generateFollowUpLetter();
                         testSubject.enqueue(testId, followUp).join();
                         enqueuedLetters.put(i, followUp);
                     });

            // when
            Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId).join().iterator();

            // then
            for (Map.Entry<Integer, DeadLetter<M>> entry : enqueuedLetters.entrySet()) {
                assertTrue(resultIterator.hasNext());
                assertLetter(entry.getValue(), resultIterator.next());
            }
        }

        @Test
        void deadLettersReturnsAllEnqueuedDeadLetters() {
            // given
            Object thisTestId = generateId();
            Object thatTestId = generateId();

            DeadLetter<? extends M> thisFirstExpected = generateInitialLetter();
            DeadLetter<? extends M> thisSecondExpected = generateInitialLetter();
            DeadLetter<? extends M> thatFirstExpected = generateInitialLetter();
            DeadLetter<? extends M> thatSecondExpected = generateInitialLetter();

            testSubject.enqueue(thisTestId, thisFirstExpected).join();
            testSubject.enqueue(thatTestId, thatFirstExpected).join();
            testSubject.enqueue(thisTestId, thisSecondExpected).join();
            testSubject.enqueue(thatTestId, thatSecondExpected).join();

            // when
            Iterator<Iterable<DeadLetter<? extends M>>> result = testSubject.deadLetters().join().iterator();

            // then
            int count = 0;
            while (result.hasNext()) {
                Iterable<DeadLetter<? extends M>> sequenceIterator = result.next();
                Iterator<DeadLetter<? extends M>> resultLetters = sequenceIterator.iterator();
                while (resultLetters.hasNext()) {
                    count += 1;
                    DeadLetter<? extends M> resultLetter = resultLetters.next();
                    if (letterMatches(thisFirstExpected).test(resultLetter)) {
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
            assertEquals(2, count);
        }

        @Test
        void isFullReturnsTrueAfterMaximumAmountOfSequencesIsReached() {
            // given
            assertFalse(testSubject.isFull(generateId()).join());
            long maxSequences = maxSequences();
            assertTrue(maxSequences > 0);
            for (int i = 0; i < maxSequences; i++) {
                testSubject.enqueue(generateId(), generateInitialLetter()).join();
            }

            // when / then
            assertTrue(testSubject.isFull(generateId()).join());
        }

        @Test
        void isFullReturnsTrueAfterMaximumSequenceSizeIsReached() {
            // given
            Object testId = generateId();
            assertFalse(testSubject.isFull(testId).join());
            long maxSequenceSize = maxSequenceSize();
            assertTrue(maxSequenceSize > 0);
            for (int i = 0; i < maxSequenceSize; i++) {
                testSubject.enqueue(testId, generateInitialLetter()).join();
            }

            // when / then
            assertTrue(testSubject.isFull(testId).join());
        }

        @Test
        void sizeReturnsOverallNumberOfContainedDeadLetters() {
            // given / when / then
            assertEquals(0, testSubject.size().join());

            Object testId = generateId();
            testSubject.enqueue(testId, generateInitialLetter()).join();
            assertEquals(1, testSubject.size().join());
            testSubject.enqueue(testId, generateInitialLetter()).join();
            assertEquals(2, testSubject.size().join());

            testSubject.enqueue(generateId(), generateInitialLetter()).join();
            assertEquals(3, testSubject.size().join());
        }

        @Test
        void sequenceSizeForSequenceIdentifierReturnsTheNumberOfContainedLettersForGivenSequenceIdentifier() {
            // given / when / then
            assertEquals(0, testSubject.sequenceSize("some-id").join());

            Object testId = generateId();
            testSubject.enqueue(testId, generateInitialLetter()).join();
            assertEquals(0, testSubject.sequenceSize("some-id").join());
            assertEquals(1, testSubject.sequenceSize(testId).join());
            testSubject.enqueue(testId, generateInitialLetter()).join();
            assertEquals(2, testSubject.sequenceSize(testId).join());

            testSubject.enqueue(generateId(), generateInitialLetter()).join();
            assertEquals(0, testSubject.sequenceSize("some-id").join());
            assertEquals(2, testSubject.sequenceSize(testId).join());
        }

        @Test
        void amountOfSequencesReturnsTheNumberOfUniqueSequences() {
            // given / when / then
            assertEquals(0, testSubject.amountOfSequences().join());

            testSubject.enqueue(generateId(), generateInitialLetter()).join();
            assertEquals(1, testSubject.amountOfSequences().join());

            testSubject.enqueue(generateId(), generateInitialLetter()).join();
            assertEquals(2, testSubject.amountOfSequences().join());

            Object testId = generateId();
            testSubject.enqueue(testId, generateInitialLetter()).join();
            testSubject.enqueue(testId, generateInitialLetter()).join();
            testSubject.enqueue(testId, generateInitialLetter()).join();
            assertEquals(3, testSubject.amountOfSequences().join());
        }
    }

    @Nested
    class WhenProcessing {

        @Test
        void processInvocationReturnsFalseIfThereAreNoLetters() {
            // given
            AtomicBoolean taskInvoked = new AtomicBoolean(false);
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                taskInvoked.set(true);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            // when
            boolean result = testSubject.process(testTask).join();

            // then
            assertFalse(result);
            assertFalse(taskInvoked.get());
        }

        @Test
        void processInvocationReturnsTrueAndEvictsTheLetter() {
            // given
            AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                resultLetter.set(letter);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter).join();

            // when
            boolean result = testSubject.process(testTask).join();

            // then
            assertTrue(result);
            assertLetter(testLetter, resultLetter.get());

            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).join().iterator();
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void processInvocationReturnsFalseAndRequeuesTheLetter() {
            // given
            AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
            Throwable testThrowable = generateThrowable();
            Metadata testDiagnostics = Metadata.with("custom-key", "custom-value");
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                resultLetter.set(letter);
                return CompletableFuture.completedFuture(Decisions.requeue(testThrowable, l -> testDiagnostics));
            };

            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter).join();

            Instant expectedLastTouched = setAndGetTime();
            DeadLetter<M> expectedRequeuedLetter =
                    generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

            // when
            boolean result = testSubject.process(testTask).join();

            // then
            assertFalse(result);
            assertLetter(testLetter, resultLetter.get());

            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(expectedRequeuedLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void processInvocationInvokesProcessingTaskInLastTouchedOrderOfLetters() {
            // given
            AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                resultLetter.set(letter);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            Object testThisId = generateId();
            DeadLetter<? extends M> testThisLetter = generateInitialLetter();
            testSubject.enqueue(testThisId, testThisLetter).join();

            setAndGetTime(Instant.now().plus(5, ChronoUnit.SECONDS));
            Object testThatId = generateId();
            DeadLetter<? extends M> testThatLetter = generateInitialLetter();
            testSubject.enqueue(testThatId, testThatLetter).join();

            // when / then
            boolean result = testSubject.process(testTask).join();
            assertTrue(result);
            assertLetter(testThisLetter, resultLetter.get());

            result = testSubject.process(testTask).join();
            assertTrue(result);
            assertLetter(testThatLetter, resultLetter.get());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void processInvocationHandlesAllLettersInSequence() {
            // given
            AtomicReference<Deque<DeadLetter<? extends M>>> resultLetters = new AtomicReference<>();
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                Deque<DeadLetter<? extends M>> sequence = resultLetters.get();
                if (sequence == null) {
                    sequence = new LinkedList<>();
                }
                sequence.addLast(letter);
                resultLetters.set(sequence);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            Object testId = generateId();
            DeadLetter<? extends M> firstTestLetter = generateInitialLetter();
            testSubject.enqueue(testId, firstTestLetter).join();
            setAndGetTime(Instant.now());
            DeadLetter<? extends M> secondTestLetter = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> secondTestLetter).join();
            setAndGetTime(Instant.now());
            DeadLetter<? extends M> thirdTestLetter = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> thirdTestLetter).join();

            // Advance time so the extra sequence has a later timestamp and won't be processed first
            setAndGetTime(Instant.now().plus(1, ChronoUnit.HOURS));
            testSubject.enqueue(generateId(), generateInitialLetter()).join();

            // when
            boolean result = testSubject.process(testTask).join();

            // then
            assertTrue(result);
            Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

            assertLetter(firstTestLetter, resultSequence.pollFirst());
            assertLetter(secondTestLetter, resultSequence.pollFirst());
            assertLetter(thirdTestLetter, resultSequence.pollFirst());
        }

        @Test
        void processHandlesMassiveAmountOfLettersInSequence() {
            // given
            AtomicReference<Deque<DeadLetter<? extends M>>> resultLetters = new AtomicReference<>();
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                Deque<DeadLetter<? extends M>> sequence = resultLetters.get();
                if (sequence == null) {
                    sequence = new LinkedList<>();
                }
                sequence.addLast(letter);
                resultLetters.set(sequence);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            Object testId = generateId();
            DeadLetter<? extends M> firstTestLetter = generateInitialLetter();
            testSubject.enqueue(testId, firstTestLetter).join();

            List<DeadLetter<M>> expectedOrderList = new LinkedList<>();
            long loopSize = maxSequences() - 5;
            for (int i = 0; i < loopSize; i++) {
                DeadLetter<M> deadLetter = generateFollowUpLetter();
                expectedOrderList.add(deadLetter);
                testSubject.enqueueIfPresent(testId, () -> deadLetter).join();
            }

            // when
            boolean result = testSubject.process(testTask).join();

            // then
            assertTrue(result);
            Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

            DeadLetter<? extends M> resultLetter = resultSequence.pollFirst();
            assertNotNull(resultLetter);
            assertLetter(firstTestLetter, resultLetter);

            for (int i = 0; i < loopSize; i++) {
                resultLetter = resultSequence.pollFirst();
                assertNotNull(resultLetter);
                assertLetter(expectedOrderList.get(i), resultLetter);
            }
        }

        @Test
        void processInvocationReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
            // given
            CountDownLatch isBlocking = new CountDownLatch(1);
            CountDownLatch hasProcessed = new CountDownLatch(1);
            AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
            AtomicBoolean invoked = new AtomicBoolean(false);

            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> blockingTask = letter -> {
                try {
                    isBlocking.countDown();
                    //noinspection ResultOfMethodCallIgnored
                    hasProcessed.await(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                resultLetter.set(letter);
                return CompletableFuture.completedFuture(Decisions.evict());
            };
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> nonBlockingTask = letter -> {
                invoked.set(true);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter).join();

            // when
            Thread blockingProcess = new Thread(() -> testSubject.process(blockingTask).join());
            blockingProcess.start();
            assertTrue(isBlocking.await(100, TimeUnit.MILLISECONDS));

            boolean result = testSubject.process(nonBlockingTask).join();

            // then
            assertFalse(result);
            assertFalse(invoked.get());

            hasProcessed.countDown();
            blockingProcess.join();
            assertLetter(testLetter, resultLetter.get());
        }
    }

    @Nested
    class WhenProcessingWithFilter {

        @Test
        void processWithLetterPredicateReturnsFalseIfThereAreNoMatchingLetters() {
            // given
            AtomicBoolean releasedLetter = new AtomicBoolean(false);
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                releasedLetter.set(true);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            testSubject.enqueue(generateId(), generateInitialLetter()).join();
            testSubject.enqueue(generateId(), generateInitialLetter()).join();

            // when
            boolean result = testSubject.process(letter -> false, testTask).join();

            // then
            assertFalse(result);
            assertFalse(releasedLetter.get());
        }

        @Test
        void processWithLetterPredicateInvokesProcessingTaskWithMatchingLetter() {
            // given
            AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                resultLetter.set(letter);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            Object testThisId = generateId();
            DeadLetter<? extends M> testThisLetter = generateInitialLetter();
            testSubject.enqueue(testThisId, testThisLetter).join();

            Object testThatId = generateId();
            DeadLetter<? extends M> testThatLetter = generateInitialLetter();
            testSubject.enqueue(testThatId, testThatLetter).join();

            // when / then
            boolean result = testSubject.process(letterMatches(testThisLetter), testTask).join();
            assertTrue(result);
            assertLetter(testThisLetter, resultLetter.get());

            result = testSubject.process(letterMatches(testThatLetter), testTask).join();
            assertTrue(result);
            assertLetter(testThatLetter, resultLetter.get());
        }

        @Test
        void processWithLetterPredicateReturnsTrueAndEvictsTheLetter() {
            // given
            AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                resultLetter.set(letter);
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            Object testId = generateId();
            Object nonMatchingId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter).join();
            testSubject.enqueue(nonMatchingId, generateInitialLetter()).join();

            // when
            boolean result = testSubject.process(
                    letter -> letter.message().payload().equals(testLetter.message().payload()),
                    testTask
            ).join();

            // then
            assertTrue(result);
            assertLetter(testLetter, resultLetter.get());

            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).join().iterator();
            assertFalse(resultLetters.hasNext());
            assertTrue(testSubject.deadLetters().join().iterator().hasNext());
        }
    }

    @Nested
    class WhenClearing {

        @Test
        void clearInvocationRemovesAllEntries() {
            // given
            Object idOne = generateId();
            Object idTwo = generateId();
            Object idThree = generateId();

            testSubject.enqueue(idOne, generateInitialLetter()).join();
            testSubject.enqueue(idTwo, generateInitialLetter()).join();
            testSubject.enqueue(idThree, generateInitialLetter()).join();

            assertTrue(testSubject.contains(idOne).join());
            assertTrue(testSubject.contains(idTwo).join());
            assertTrue(testSubject.contains(idThree).join());

            // when
            testSubject.clear().join();

            // then
            assertFalse(testSubject.contains(idOne).join());
            assertFalse(testSubject.contains(idTwo).join());
            assertFalse(testSubject.contains(idThree).join());
        }
    }

    // Helper methods

    private Predicate<DeadLetter<? extends M>> letterMatches(DeadLetter<? extends M> expected) {
        return actual -> expected.message().identifier().equals(actual.message().identifier());
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
    protected static EventMessage generateEvent() {
        return EventTestUtils.asEventMessage("Then this happened..." + UUID.randomUUID());
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
     * Generate an initial {@link DeadLetter} implementation expected by the test subject.
     *
     * @return A {@link DeadLetter} implementation expected by the test subject.
     */
    protected abstract DeadLetter<M> generateInitialLetter();

    /**
     * Generate a follow-up {@link DeadLetter} implementation expected by the test subject.
     *
     * @return A follow-up {@link DeadLetter} implementation expected by the test subject.
     */
    protected abstract DeadLetter<M> generateFollowUpLetter();

    /**
     * Generates a {@link DeadLetter} implementation specific to the {@link SequencedDeadLetterQueue} tested.
     *
     * @param deadLetter The dead letter to convert.
     * @return The converted dead letter.
     */
    protected DeadLetter<M> mapToQueueImplementation(DeadLetter<M> deadLetter) {
        return deadLetter;
    }

    /**
     * Generate a {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued.
     *
     * @param original     The original {@link DeadLetter} to base the requeued dead letter on.
     * @param requeueCause The cause for requeueing the {@code original}.
     * @return A {@link DeadLetter} implementation expected by the test subject based on the given {@code original}.
     */
    protected DeadLetter<M> generateRequeuedLetter(DeadLetter<M> original, Throwable requeueCause) {
        Instant lastTouched = setAndGetTime();
        return generateRequeuedLetter(original, lastTouched, requeueCause, Metadata.emptyInstance());
    }

    /**
     * Generate a {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued.
     *
     * @param original     The original {@link DeadLetter} to base the requeued dead letter on.
     * @param lastTouched  The {@link DeadLetter#lastTouched()} of the generated {@link DeadLetter} implementation.
     * @param requeueCause The cause for requeueing the {@code original}.
     * @param diagnostics  The diagnostics {@link Metadata} added to the requeued letter.
     * @return A {@link DeadLetter} implementation expected by the test subject based on the given {@code original}.
     */
    protected DeadLetter<M> generateRequeuedLetter(DeadLetter<M> original,
                                                   Instant lastTouched,
                                                   Throwable requeueCause,
                                                   Metadata diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause)
                       .withDiagnostics(diagnostics)
                       .markTouched();
    }

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
     * Set the {@link Clock} used by this test.
     *
     * @param clock The clock to use during testing.
     */
    protected abstract void setClock(Clock clock);

    /**
     * Assert whether the {@code expected} {@link DeadLetter} matches the {@code actual} {@code DeadLetter}.
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

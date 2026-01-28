/*
 * Copyright (c) 2010-2026. Axon Framework
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract class providing a generic test suite every {@link SyncSequencedDeadLetterQueue} implementation should comply
 * with.
 * <p>
 * All tests are designed for the synchronous API.
 *
 * @param <M> The {@link DeadLetter} implementation enqueued by this test class.
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public abstract class SyncSequencedDeadLetterQueueTest<M extends Message> {

    private SyncSequencedDeadLetterQueue<M> testSubject;

    @BeforeEach
    void setUp() {
        // Reset clock to current time to avoid timestamp issues from previous tests
        setClock(Clock.systemDefaultZone());
        testSubject = buildTestSubject();
    }

    /**
     * Constructs the {@link SyncSequencedDeadLetterQueue} implementation under test.
     *
     * @return A {@link SyncSequencedDeadLetterQueue} implementation under test.
     */
    protected abstract SyncSequencedDeadLetterQueue<M> buildTestSubject();

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
            testSubject.enqueue(testId, testLetter);

            // then
            assertTrue(testSubject.contains(testId));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
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
                testSubject.enqueue(generateId(), generateInitialLetter());
            }

            // when / then
            Object oneSequenceToMany = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            assertThatThrownBy(() -> testSubject.enqueue(oneSequenceToMany, testLetter))
                    .isInstanceOf(DeadLetterQueueOverflowException.class);
        }

        @Test
        void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequenceSizeIsReached() {
            // given
            Object testId = generateId();
            long maxSequenceSize = maxSequenceSize();
            assertTrue(maxSequenceSize > 0);
            for (int i = 0; i < maxSequenceSize; i++) {
                testSubject.enqueue(testId, generateInitialLetter());
            }

            // when / then
            DeadLetter<M> oneLetterToMany = generateInitialLetter();
            assertThatThrownBy(() -> testSubject.enqueue(testId, oneLetterToMany))
                    .isInstanceOf(DeadLetterQueueOverflowException.class);
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
                testSubject.enqueue(testId, generateInitialLetter());
            }

            // when / then
            assertThatThrownBy(() -> testSubject.enqueueIfPresent(testId,
                    SyncSequencedDeadLetterQueueTest.this::generateFollowUpLetter))
                    .isInstanceOf(DeadLetterQueueOverflowException.class);
        }

        @Test
        void enqueueIfPresentDoesNotEnqueueForEmptyQueue() {
            // given
            Object testId = generateId();

            // when
            boolean result = testSubject.enqueueIfPresent(testId,
                    SyncSequencedDeadLetterQueueTest.this::generateFollowUpLetter);

            // then
            assertFalse(result);
            assertFalse(testSubject.contains(testId));
        }

        @Test
        void enqueueIfPresentDoesNotEnqueueForNonExistentSequenceIdentifier() {
            // given
            Object testFirstId = generateId();
            testSubject.enqueue(testFirstId, generateInitialLetter());
            Object testSecondId = generateId();

            // when
            boolean result = testSubject.enqueueIfPresent(testSecondId,
                    SyncSequencedDeadLetterQueueTest.this::generateFollowUpLetter);

            // then
            assertFalse(result);
            assertTrue(testSubject.contains(testFirstId));
            assertFalse(testSubject.contains(testSecondId));
        }

        @Test
        void enqueueIfPresentEnqueuesForExistingSequenceIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<M> testFirstLetter = generateInitialLetter();
            DeadLetter<M> testSecondLetter = generateFollowUpLetter();

            // when
            testSubject.enqueue(testId, testFirstLetter);
            testSubject.enqueueIfPresent(testId, () -> testSecondLetter);

            // then
            assertTrue(testSubject.contains(testId));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
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
            testSubject.enqueue(testId, testLetter);

            // when
            testSubject.evict(mapToQueueImplementation(generateInitialLetter()));

            // then
            assertTrue(testSubject.contains(testId));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(testLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void evictDoesNotChangeTheQueueForNonExistentLetterIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter);

            // when
            testSubject.evict(mapToQueueImplementation(generateInitialLetter()));

            // then
            assertTrue(testSubject.contains(testId));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(testLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void evictRemovesLetterFromQueue() {
            // given
            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter);
            DeadLetter<? extends M> resultLetter = testSubject.deadLetterSequence(testId).iterator().next();

            // when
            testSubject.evict(resultLetter);

            // then
            assertFalse(testSubject.contains(testId));
            assertFalse(testSubject.deadLetters().iterator().hasNext());
        }
    }

    @Nested
    class WhenRequeueing {

        @Test
        void requeueThrowsNoSuchDeadLetterExceptionForNonExistentSequenceIdentifier() {
            // given
            DeadLetter<M> testLetter = generateInitialLetter();

            // when / then
            assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(testLetter), l -> l))
                    .isInstanceOf(NoSuchDeadLetterException.class);
        }

        @Test
        void requeueThrowsNoSuchDeadLetterExceptionForNonExistentLetterIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            DeadLetter<M> otherTestLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter);

            // when / then
            assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(otherTestLetter), l -> l))
                    .isInstanceOf(NoSuchDeadLetterException.class);
        }

        @Test
        void requeueReentersLetterToQueueWithUpdatedLastTouchedAndCause() {
            // given
            Object testId = generateId();
            DeadLetter<M> testLetter = generateInitialLetter();
            Throwable testCause = generateThrowable();
            testSubject.enqueue(testId, testLetter);
            DeadLetter<? extends M> resultLetter = testSubject.deadLetterSequence(testId).iterator().next();
            DeadLetter<M> expectedLetter = generateRequeuedLetter(testLetter, testCause);

            // when
            testSubject.requeue(resultLetter, l -> l.withCause(testCause));

            // then
            assertTrue(testSubject.contains(testId));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
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
            assertFalse(testSubject.contains(testId));
            testSubject.enqueue(testId, generateInitialLetter());
            assertTrue(testSubject.contains(testId));
            assertFalse(testSubject.contains(otherTestId));
        }

        @Test
        void deadLetterSequenceReturnsEnqueuedLettersMatchingGivenSequenceIdentifier() {
            // given
            Object testId = generateId();
            DeadLetter<M> expected = generateInitialLetter();

            // when / then
            Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId).iterator();
            assertFalse(resultIterator.hasNext());

            testSubject.enqueue(testId, expected);

            resultIterator = testSubject.deadLetterSequence(testId).iterator();
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
            testSubject.enqueue(testId, initial);
            enqueuedLetters.put(0, initial);

            IntStream.range(1, Long.valueOf(maxSequenceSize()).intValue())
                     .forEach(i -> {
                         DeadLetter<M> followUp = generateFollowUpLetter();
                         testSubject.enqueue(testId, followUp);
                         enqueuedLetters.put(i, followUp);
                     });

            // when
            Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId).iterator();

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

            testSubject.enqueue(thisTestId, thisFirstExpected);
            testSubject.enqueueIfPresent(thisTestId, () -> thisSecondExpected);
            testSubject.enqueue(thatTestId, thatFirstExpected);
            testSubject.enqueueIfPresent(thatTestId, () -> thatSecondExpected);

            // when
            Iterator<Iterable<DeadLetter<? extends M>>> resultIterator = testSubject.deadLetters().iterator();

            // then
            assertTrue(resultIterator.hasNext());
            assertNotNull(resultIterator.next());
            assertTrue(resultIterator.hasNext());
            assertNotNull(resultIterator.next());
            assertFalse(resultIterator.hasNext());
        }

        @Test
        void isFullReturnsTrueWhenMaxSequencesIsReached() {
            // given
            Object testId = generateId();
            for (int i = 0; i < maxSequences(); i++) {
                testSubject.enqueue(generateId(), generateInitialLetter());
            }

            // when / then
            assertTrue(testSubject.isFull(testId));
        }

        @Test
        void isFullReturnsTrueWhenMaxSequenceSizeIsReached() {
            // given
            Object testId = generateId();
            for (int i = 0; i < maxSequenceSize(); i++) {
                testSubject.enqueue(testId, generateInitialLetter());
            }

            // when / then
            assertTrue(testSubject.isFull(testId));
        }

        @Test
        void sizeReturnsCorrectAmount() {
            // given / when / then
            assertEquals(0, testSubject.size());

            testSubject.enqueue(generateId(), generateInitialLetter());
            assertEquals(1, testSubject.size());

            testSubject.enqueue(generateId(), generateInitialLetter());
            assertEquals(2, testSubject.size());

            Object testId = generateId();
            testSubject.enqueue(testId, generateInitialLetter());
            testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter);
            assertEquals(4, testSubject.size());
        }

        @Test
        void sequenceSizeReturnsCorrectAmount() {
            // given
            Object testId = generateId();

            // when / then
            assertEquals(0, testSubject.sequenceSize(testId));

            testSubject.enqueue(testId, generateInitialLetter());
            assertEquals(1, testSubject.sequenceSize(testId));

            testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter);
            testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter);
            assertEquals(3, testSubject.sequenceSize(testId));
        }

        @Test
        void amountOfSequencesReturnsCorrectAmount() {
            // given / when / then
            assertEquals(0, testSubject.amountOfSequences());

            Object testId = generateId();
            testSubject.enqueue(testId, generateInitialLetter());
            assertEquals(1, testSubject.amountOfSequences());
            testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter);
            assertEquals(1, testSubject.amountOfSequences());

            testSubject.enqueue(generateId(), generateInitialLetter());
            assertEquals(2, testSubject.amountOfSequences());
        }

        private DeadLetter<M> generateFollowUpLetter() {
            return SyncSequencedDeadLetterQueueTest.this.generateFollowUpLetter();
        }
    }

    @Nested
    class WhenProcessing {

        @Test
        void processReturnsFalseForEmptyQueue() {
            // given
            AtomicBoolean invoked = new AtomicBoolean(false);
            Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
                invoked.set(true);
                return Decisions.evict();
            };

            // when
            boolean result = testSubject.process(testTask);

            // then
            assertFalse(result);
            assertFalse(invoked.get());
        }

        @Test
        void processInvokesProcessingTaskWithLetter() {
            // given
            AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
            Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
                resultLetter.set(letter);
                return Decisions.evict();
            };

            Object testId = generateId();
            DeadLetter<? extends M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter);

            // when
            boolean result = testSubject.process(testTask);

            // then
            assertTrue(result);
            assertLetter(testLetter, resultLetter.get());
        }

        @Test
        void processReturnsTrueAndEvictsLetter() {
            // given
            AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
            Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
                resultLetter.set(letter);
                return Decisions.evict();
            };

            Object testId = generateId();
            DeadLetter<? extends M> testLetter = generateInitialLetter();
            testSubject.enqueue(testId, testLetter);

            // when
            boolean result = testSubject.process(testTask);

            // then
            assertTrue(result);
            assertLetter(testLetter, resultLetter.get());
            assertFalse(testSubject.deadLetters().iterator().hasNext());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void processInvocationHandlesAllLettersInSequence() {
            // given
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
            setAndGetTime(Instant.now());
            DeadLetter<? extends M> secondTestLetter = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> secondTestLetter);
            setAndGetTime(Instant.now());
            DeadLetter<? extends M> thirdTestLetter = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> thirdTestLetter);

            // Advance time so the extra sequence has a later timestamp and won't be processed first
            setAndGetTime(Instant.now().plus(1, ChronoUnit.HOURS));
            testSubject.enqueue(generateId(), generateInitialLetter());

            // when
            boolean result = testSubject.process(testTask);

            // then
            assertTrue(result);
            Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

            assertLetter(firstTestLetter, resultSequence.pollFirst());
            assertLetter(secondTestLetter, resultSequence.pollFirst());
            assertLetter(thirdTestLetter, resultSequence.pollFirst());
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

            testSubject.enqueue(idOne, generateInitialLetter());
            testSubject.enqueue(idTwo, generateInitialLetter());
            testSubject.enqueue(idThree, generateInitialLetter());

            assertTrue(testSubject.contains(idOne));
            assertTrue(testSubject.contains(idTwo));
            assertTrue(testSubject.contains(idThree));

            // when
            testSubject.clear();

            // then
            assertFalse(testSubject.contains(idOne));
            assertFalse(testSubject.contains(idTwo));
            assertFalse(testSubject.contains(idThree));
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
     * Generates a {@link DeadLetter} implementation specific to the {@link SyncSequencedDeadLetterQueue} tested.
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

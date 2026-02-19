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

import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
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

    /**
     * Convenience method to enqueue a generated dead letter with its associated context.
     *
     * @param sequenceId The identifier of the sequence to enqueue to.
     * @param generated  The generated dead letter paired with its context.
     */
    private void enqueue(Object sequenceId, DeadLetterWithContext<? extends M> generated) {
        testSubject.enqueue(sequenceId, generated.letter(), toProcessingContext(generated.context()));
    }

    /**
     * Asserts that the {@code actual} {@link DeadLetter} retrieved from the queue matches the {@code expected}
     * {@link DeadLetterWithContext}, comparing both the letter and the context (extracted via
     * {@link #extractContext(DeadLetter)}).
     */
    private void assertLetterWithContext(DeadLetterWithContext<? extends M> expected,
                                         DeadLetter<? extends M> actual) {
        assertLetter(expected.letter(), actual);
        assertContext(expected.context(), extractContext(actual));
    }

    /**
     * Converts a {@link Context} to a {@link ProcessingContext} using {@link StubProcessingContext#fromContext(Context)},
     * or returns {@code null} if the given context is {@code null}.
     */
    private static ProcessingContext toProcessingContext(Context context) {
        return context != null ? StubProcessingContext.fromContext(context) : null;
    }

    @Nested
    class WhenEnqueueing {

        @Test
        void enqueueAddsDeadLetter() {
            // given
            Object testId = generateId();
            var generated = generateInitialLetter();

            // when
            enqueue(testId, generated);

            // then
            assertTrue(testSubject.contains(testId, null));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).iterator();
            assertTrue(resultLetters.hasNext());
            assertLetterWithContext(generated, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequencesIsReached() {
            // given
            long maxSequences = maxSequences();
            assertTrue(maxSequences > 0);
            for (int i = 0; i < maxSequences; i++) {
                enqueue(generateId(), generateInitialLetter());
            }

            // when / then
            Object oneSequenceToMany = generateId();
            var generated = generateInitialLetter();
            assertThatThrownBy(() -> testSubject.enqueue(oneSequenceToMany, generated.letter(), toProcessingContext(generated.context())))
                    .isInstanceOf(DeadLetterQueueOverflowException.class);
        }

        @Test
        void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequenceSizeIsReached() {
            // given
            Object testId = generateId();
            long maxSequenceSize = maxSequenceSize();
            assertTrue(maxSequenceSize > 0);
            for (int i = 0; i < maxSequenceSize; i++) {
                enqueue(testId, generateInitialLetter());
            }

            // when / then
            var generated = generateInitialLetter();
            assertThatThrownBy(() -> testSubject.enqueue(testId, generated.letter(), toProcessingContext(generated.context())))
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
                enqueue(testId, generateInitialLetter());
            }

            // when / then
            var followUp = generateFollowUpLetter();
            assertThatThrownBy(() -> testSubject.enqueueIfPresent(testId,
                    () -> followUp.letter(), toProcessingContext(followUp.context())))
                    .isInstanceOf(DeadLetterQueueOverflowException.class);
        }

        @Test
        void enqueueIfPresentDoesNotEnqueueForEmptyQueue() {
            // given
            Object testId = generateId();

            // when
            var followUp = generateFollowUpLetter();
            boolean result = testSubject.enqueueIfPresent(testId,
                    () -> followUp.letter(), toProcessingContext(followUp.context()));

            // then
            assertFalse(result);
            assertFalse(testSubject.contains(testId, null));
        }

        @Test
        void enqueueIfPresentDoesNotEnqueueForNonExistentSequenceIdentifier() {
            // given
            Object testFirstId = generateId();
            enqueue(testFirstId, generateInitialLetter());
            Object testSecondId = generateId();

            // when
            var followUp = generateFollowUpLetter();
            boolean result = testSubject.enqueueIfPresent(testSecondId,
                    () -> followUp.letter(), toProcessingContext(followUp.context()));

            // then
            assertFalse(result);
            assertTrue(testSubject.contains(testFirstId, null));
            assertFalse(testSubject.contains(testSecondId, null));
        }

        @Test
        void enqueueIfPresentEnqueuesForExistingSequenceIdentifier() {
            // given
            Object testId = generateId();
            var firstGenerated = generateInitialLetter();
            var secondGenerated = generateFollowUpLetter();

            // when
            enqueue(testId, firstGenerated);
            testSubject.enqueueIfPresent(testId, () -> secondGenerated.letter(), toProcessingContext(secondGenerated.context()));

            // then
            assertTrue(testSubject.contains(testId, null));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).iterator();
            assertTrue(resultLetters.hasNext());
            assertLetterWithContext(firstGenerated, resultLetters.next());
            assertTrue(resultLetters.hasNext());
            assertLetterWithContext(secondGenerated, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }
    }

    @Nested
    class WhenEvicting {

        @Test
        void evictDoesNotChangeTheQueueForNonExistentSequenceIdentifier() {
            // given
            Object testId = generateId();
            var generated = generateInitialLetter();
            enqueue(testId, generated);

            // when
            testSubject.evict(mapToQueueImplementation(generateInitialLetter()), null);

            // then
            assertTrue(testSubject.contains(testId, null));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).iterator();
            assertTrue(resultLetters.hasNext());
            assertLetterWithContext(generated, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void evictDoesNotChangeTheQueueForNonExistentLetterIdentifier() {
            // given
            Object testId = generateId();
            var generated = generateInitialLetter();
            enqueue(testId, generated);

            // when
            testSubject.evict(mapToQueueImplementation(generateInitialLetter()), null);

            // then
            assertTrue(testSubject.contains(testId, null));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).iterator();
            assertTrue(resultLetters.hasNext());
            assertLetterWithContext(generated, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void evictRemovesLetterFromQueue() {
            // given
            Object testId = generateId();
            enqueue(testId, generateInitialLetter());
            DeadLetter<? extends M> resultLetter = testSubject.deadLetterSequence(testId, null).iterator().next();

            // when
            testSubject.evict(resultLetter, null);

            // then
            assertFalse(testSubject.contains(testId, null));
            assertFalse(testSubject.deadLetters(null).iterator().hasNext());
        }
    }

    @Nested
    class WhenRequeueing {

        @Test
        void requeueThrowsNoSuchDeadLetterExceptionForNonExistentSequenceIdentifier() {
            // given
            var testGenerated = generateInitialLetter();

            // when / then
            assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(testGenerated), l -> l, null))
                    .isInstanceOf(NoSuchDeadLetterException.class);
        }

        @Test
        void requeueThrowsNoSuchDeadLetterExceptionForNonExistentLetterIdentifier() {
            // given
            Object testId = generateId();
            var generated = generateInitialLetter();
            var otherGenerated = generateInitialLetter();
            enqueue(testId, generated);

            // when / then
            assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(otherGenerated), l -> l, null))
                    .isInstanceOf(NoSuchDeadLetterException.class);
        }

        @Test
        void requeueReentersLetterToQueueWithUpdatedLastTouchedAndCause() {
            // given
            Object testId = generateId();
            var generated = generateInitialLetter();
            Throwable testCause = generateThrowable();
            enqueue(testId, generated);
            DeadLetter<? extends M> resultLetter = testSubject.deadLetterSequence(testId, null).iterator().next();
            DeadLetter<M> expectedLetter = generateRequeuedLetter(generated.letter(), testCause);

            // when
            testSubject.requeue(resultLetter, l -> l.withCause(testCause), null);

            // then
            assertTrue(testSubject.contains(testId, null));
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).iterator();
            assertTrue(resultLetters.hasNext());
            DeadLetter<? extends M> requeuedLetter = resultLetters.next();
            assertLetter(expectedLetter, requeuedLetter);
            assertContext(generated.context(), extractContext(requeuedLetter));
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
            assertFalse(testSubject.contains(testId, null));
            enqueue(testId, generateInitialLetter());
            assertTrue(testSubject.contains(testId, null));
            assertFalse(testSubject.contains(otherTestId, null));
        }

        @Test
        void deadLetterSequenceReturnsEnqueuedLettersMatchingGivenSequenceIdentifier() {
            // given
            Object testId = generateId();
            var generated = generateInitialLetter();

            // when / then
            Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId, null).iterator();
            assertFalse(resultIterator.hasNext());

            enqueue(testId, generated);

            resultIterator = testSubject.deadLetterSequence(testId, null).iterator();
            assertTrue(resultIterator.hasNext());
            assertLetterWithContext(generated, resultIterator.next());
            assertFalse(resultIterator.hasNext());
        }

        @Test
        void deadLetterSequenceReturnsMatchingEnqueuedLettersInInsertOrder() {
            // given
            Object testId = generateId();
            LinkedHashMap<Integer, DeadLetterWithContext<M>> enqueuedLetters = new LinkedHashMap<>();
            var initialGenerated = generateInitialLetter();
            enqueue(testId, initialGenerated);
            enqueuedLetters.put(0, initialGenerated);

            IntStream.range(1, Long.valueOf(maxSequenceSize()).intValue())
                     .forEach(i -> {
                         var followUpGenerated = generateFollowUpLetter();
                         testSubject.enqueue(testId, followUpGenerated.letter(), toProcessingContext(followUpGenerated.context()));
                         enqueuedLetters.put(i, followUpGenerated);
                     });

            // when
            Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId, null).iterator();

            // then
            for (Map.Entry<Integer, DeadLetterWithContext<M>> entry : enqueuedLetters.entrySet()) {
                assertTrue(resultIterator.hasNext());
                assertLetterWithContext(entry.getValue(), resultIterator.next());
            }
        }

        @Test
        void deadLettersReturnsAllEnqueuedDeadLetters() {
            // given
            Object thisTestId = generateId();
            Object thatTestId = generateId();

            var thisFirstGenerated = generateInitialLetter();
            var thisSecondGenerated = generateInitialLetter();
            var thatFirstGenerated = generateInitialLetter();
            var thatSecondGenerated = generateInitialLetter();

            enqueue(thisTestId, thisFirstGenerated);
            testSubject.enqueueIfPresent(thisTestId, () -> thisSecondGenerated.letter(), toProcessingContext(thisSecondGenerated.context()));
            enqueue(thatTestId, thatFirstGenerated);
            testSubject.enqueueIfPresent(thatTestId, () -> thatSecondGenerated.letter(), toProcessingContext(thatSecondGenerated.context()));

            // when
            Iterator<Iterable<DeadLetter<? extends M>>> resultIterator = testSubject.deadLetters(null).iterator();

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
                enqueue(generateId(), generateInitialLetter());
            }

            // when / then
            assertTrue(testSubject.isFull(testId, null));
        }

        @Test
        void isFullReturnsTrueWhenMaxSequenceSizeIsReached() {
            // given
            Object testId = generateId();
            for (int i = 0; i < maxSequenceSize(); i++) {
                enqueue(testId, generateInitialLetter());
            }

            // when / then
            assertTrue(testSubject.isFull(testId, null));
        }

        @Test
        void sizeReturnsCorrectAmount() {
            // given / when / then
            assertEquals(0, testSubject.size(null));

            enqueue(generateId(), generateInitialLetter());
            assertEquals(1, testSubject.size(null));

            enqueue(generateId(), generateInitialLetter());
            assertEquals(2, testSubject.size(null));

            Object testId = generateId();
            enqueue(testId, generateInitialLetter());
            var followUp = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> followUp.letter(), toProcessingContext(followUp.context()));
            assertEquals(4, testSubject.size(null));
        }

        @Test
        void sequenceSizeReturnsCorrectAmount() {
            // given
            Object testId = generateId();

            // when / then
            assertEquals(0, testSubject.sequenceSize(testId, null));

            enqueue(testId, generateInitialLetter());
            assertEquals(1, testSubject.sequenceSize(testId, null));

            var followUp1 = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> followUp1.letter(), toProcessingContext(followUp1.context()));
            var followUp2 = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> followUp2.letter(), toProcessingContext(followUp2.context()));
            assertEquals(3, testSubject.sequenceSize(testId, null));
        }

        @Test
        void amountOfSequencesReturnsCorrectAmount() {
            // given / when / then
            assertEquals(0, testSubject.amountOfSequences(null));

            Object testId = generateId();
            enqueue(testId, generateInitialLetter());
            assertEquals(1, testSubject.amountOfSequences(null));
            var followUp = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> followUp.letter(), toProcessingContext(followUp.context()));
            assertEquals(1, testSubject.amountOfSequences(null));

            enqueue(generateId(), generateInitialLetter());
            assertEquals(2, testSubject.amountOfSequences(null));
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
            boolean result = testSubject.process(testTask, null);

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
            var generated = generateInitialLetter();
            enqueue(testId, generated);

            // when
            boolean result = testSubject.process(testTask, null);

            // then
            assertTrue(result);
            assertLetterWithContext(generated, resultLetter.get());
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
            var generated = generateInitialLetter();
            enqueue(testId, generated);

            // when
            boolean result = testSubject.process(testTask, null);

            // then
            assertTrue(result);
            assertLetterWithContext(generated, resultLetter.get());
            assertFalse(testSubject.deadLetters(null).iterator().hasNext());
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
            var firstGenerated = generateInitialLetter();
            enqueue(testId, firstGenerated);
            setAndGetTime(Instant.now());
            var secondGenerated = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> secondGenerated.letter(), toProcessingContext(secondGenerated.context()));
            setAndGetTime(Instant.now());
            var thirdGenerated = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId, () -> thirdGenerated.letter(), toProcessingContext(thirdGenerated.context()));

            // Advance time so the extra sequence has a later timestamp and won't be processed first
            setAndGetTime(Instant.now().plus(1, ChronoUnit.HOURS));
            enqueue(generateId(), generateInitialLetter());

            // when
            boolean result = testSubject.process(testTask, null);

            // then
            assertTrue(result);
            Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

            assertLetterWithContext(firstGenerated, resultSequence.pollFirst());
            assertLetterWithContext(secondGenerated, resultSequence.pollFirst());
            assertLetterWithContext(thirdGenerated, resultSequence.pollFirst());
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

            enqueue(idOne, generateInitialLetter());
            enqueue(idTwo, generateInitialLetter());
            enqueue(idThree, generateInitialLetter());

            assertTrue(testSubject.contains(idOne, null));
            assertTrue(testSubject.contains(idTwo, null));
            assertTrue(testSubject.contains(idThree, null));

            // when
            testSubject.clear(null);

            // then
            assertFalse(testSubject.contains(idOne, null));
            assertFalse(testSubject.contains(idTwo, null));
            assertFalse(testSubject.contains(idThree, null));
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
     * Generate an initial {@link DeadLetterWithContext} containing a {@link DeadLetter} and its associated context.
     * <p>
     * The context may carry resources needed by the queue implementation during enqueueing (e.g. tracking token,
     * aggregate info for JPA-backed queues).
     *
     * @return A {@link DeadLetterWithContext} with the initial dead letter and its context.
     */
    protected abstract DeadLetterWithContext<M> generateInitialLetter();

    /**
     * Generate a follow-up {@link DeadLetterWithContext} containing a {@link DeadLetter} and its associated context.
     * <p>
     * The context may carry resources needed by the queue implementation during enqueueing (e.g. tracking token,
     * aggregate info for JPA-backed queues).
     *
     * @return A {@link DeadLetterWithContext} with the follow-up dead letter and its context.
     */
    protected abstract DeadLetterWithContext<M> generateFollowUpLetter();

    /**
     * Generates a {@link DeadLetter} implementation specific to the {@link SyncSequencedDeadLetterQueue} tested, using
     * the provided {@link DeadLetterWithContext} which pairs the dead letter with its associated {@link Context}.
     *
     * @param letterWithContext The dead letter paired with its context.
     * @return The converted dead letter.
     */
    protected DeadLetter<M> mapToQueueImplementation(DeadLetterWithContext<M> letterWithContext) {
        return letterWithContext.letter();
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

    /**
     * Assert whether the {@code expected} {@link DeadLetterWithContext} matches the {@code actual}
     * {@code DeadLetterWithContext}, comparing both the dead letters and their associated contexts.
     *
     * @param expected The expected dead letter with context.
     * @param actual   The actual dead letter with context.
     */
    protected void assertLetter(DeadLetterWithContext<? extends M> expected,
                                DeadLetterWithContext<? extends M> actual) {
        assertLetter(expected.letter(), actual.letter());
        assertContext(expected.context(), actual.context());
    }

    /**
     * Assert whether the {@code expected} {@link Context} matches the {@code actual} {@code Context} by comparing
     * their resources.
     * <p>
     * Subclasses may override this to provide implementation-specific context comparison.
     *
     * @param expected The expected context.
     * @param actual   The actual context.
     */
    protected void assertContext(Context expected, Context actual) {
        if (expected == null && actual == null) {
            return;
        }
        assertNotNull(expected, "Expected context was null but actual was not");
        assertNotNull(actual, "Actual context was null but expected was not");
        assertEquals(expected.resources(), actual.resources());
    }

    /**
     * Extracts the {@link Context} from a {@link DeadLetter} retrieved from the queue. Subclasses should override this
     * to extract context from their specific {@code DeadLetter} implementation (e.g. {@code JpaDeadLetter.context()}).
     * <p>
     * Returns {@code null} by default, indicating no context is available for comparison.
     *
     * @param deadLetter The dead letter retrieved from the queue.
     * @return The context associated with the dead letter, or {@code null}.
     */
    protected Context extractContext(DeadLetter<? extends M> deadLetter) {
        return null;
    }
}

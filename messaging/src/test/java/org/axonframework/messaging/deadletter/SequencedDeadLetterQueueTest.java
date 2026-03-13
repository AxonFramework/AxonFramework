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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Abstract class providing a generic test suite every {@link SequencedDeadLetterQueue} implementation should comply
 * with.
 * <p>
 * Note that this test suite does not use an actual {@link ProcessingContext} instance. Instead, it relies on {@code null} or
 * {@link StubProcessingContext} to satisfy the API contract without involving real lifecycle management. Integration
 * tests are expected to validate the {@link SequencedDeadLetterQueue} with an actual {@link ProcessingContext},
 * typically within a started Event Processor.
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

    /**
     * Convenience method to enqueue a generated dead letter with the processing context derived from
     * {@link DeadLetter#context()}.
     *
     * @param sequenceId The identifier of the sequence to enqueue to.
     * @param letter     The dead letter to enqueue.
     */
    private void enqueue(Object sequenceId, DeadLetter<? extends M> letter) {
        testSubject.enqueue(sequenceId, letter, toProcessingContext(letter.context())).join();
    }

    /**
     * Converts a {@link Context} to a {@link ProcessingContext} using {@link StubProcessingContext#fromContext(Context)},
     * or returns {@code null} if the given context is {@code null} or empty.
     */
    protected ProcessingContext toProcessingContext(Context context) {
        return context != null && !context.resources().isEmpty()
                ? StubProcessingContext.fromContext(context)
                : null;
    }

    @Nested
    class WhenEnqueueing {

        @Test
        void enqueueAddsDeadLetter() {
            // given
            Object testId = generateId();
            var letter = generateInitialLetter();

            // when
            enqueue(testId, letter);

            // then
            assertTrue(testSubject.contains(testId, null).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(letter, resultLetters.next());
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
            var letter = generateInitialLetter();
            assertThatThrownBy(() -> testSubject.enqueue(oneSequenceToMany,
                                                         letter,
                                                         toProcessingContext(letter.context()))
                                                   .join())
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
                enqueue(testId, generateInitialLetter());
            }

            // when / then
            var letter = generateInitialLetter();
            assertThatThrownBy(() -> testSubject.enqueue(testId,
                                                         letter,
                                                         toProcessingContext(letter.context()))
                                                   .join())
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
                enqueue(testId, generateInitialLetter());
            }

            // when / then
            var followUp = generateFollowUpLetter();
            assertThatThrownBy(() -> testSubject.enqueueIfPresent(testId,
                                                                 () -> followUp,
                                                                 toProcessingContext(followUp.context()))
                                                   .join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(DeadLetterQueueOverflowException.class);
        }

        @Test
        void enqueueIfPresentDoesNotEnqueueForEmptyQueue() {
            // given
            Object testId = generateId();

            // when
            var followUp = generateFollowUpLetter();
            boolean result = testSubject.enqueueIfPresent(testId,
                                                          () -> followUp,
                                                          toProcessingContext(followUp.context()))
                                               .join();

            // then
            assertFalse(result);
            assertFalse(testSubject.contains(testId, null).join());
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
                                                          () -> followUp,
                                                          toProcessingContext(followUp.context()))
                                               .join();

            // then
            assertFalse(result);
            assertTrue(testSubject.contains(testFirstId, null).join());
            assertFalse(testSubject.contains(testSecondId, null).join());
        }

        @Test
        void enqueueIfPresentEnqueuesForExistingSequenceIdentifier() {
            // given
            Object testId = generateId();
            var firstLetter = generateInitialLetter();
            var secondLetter = generateFollowUpLetter();

            // when
            enqueue(testId, firstLetter);
            testSubject.enqueueIfPresent(testId,
                                         () -> secondLetter,
                                         toProcessingContext(secondLetter.context()))
                       .join();

            // then
            assertTrue(testSubject.contains(testId, null).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(firstLetter, resultLetters.next());
            assertTrue(resultLetters.hasNext());
            assertLetter(secondLetter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }
    }

    @Nested
    class WhenEvicting {

        @Test
        void evictDoesNotChangeTheQueueForNonExistentSequenceIdentifier() {
            // given
            Object testId = generateId();
            var letter = generateInitialLetter();
            enqueue(testId, letter);

            // when
            testSubject.evict(mapToQueueImplementation(generateInitialLetter()), null).join();

            // then
            assertTrue(testSubject.contains(testId, null).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(letter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void evictDoesNotChangeTheQueueForNonExistentLetterIdentifier() {
            // given
            Object testId = generateId();
            var letter = generateInitialLetter();
            enqueue(testId, letter);

            // when
            testSubject.evict(mapToQueueImplementation(generateInitialLetter()), null).join();

            // then
            assertTrue(testSubject.contains(testId, null).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertTrue(resultLetters.hasNext());
            assertLetter(letter, resultLetters.next());
            assertFalse(resultLetters.hasNext());
        }

        @Test
        void evictRemovesLetterFromQueue() {
            // given
            Object testId = generateId();
            var letter = generateInitialLetter();
            enqueue(testId, letter);
            DeadLetter<? extends M> resultLetter = testSubject.deadLetterSequence(testId, null).join().iterator().next();

            // when
            testSubject.evict(resultLetter, null).join();

            // then
            assertFalse(testSubject.contains(testId, null).join());
            assertFalse(testSubject.deadLetters(null).join().iterator().hasNext());
        }
    }

    @Nested
    class WhenRequeueing {

        @Test
        void requeueThrowsNoSuchDeadLetterExceptionForNonExistentSequenceIdentifier() {
            // given
            var testLetter = generateInitialLetter();

            // when / then
            assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(testLetter), l -> l, null).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(NoSuchDeadLetterException.class);
        }

        @Test
        void requeueThrowsNoSuchDeadLetterExceptionForNonExistentLetterIdentifier() {
            // given
            Object testId = generateId();
            var letter = generateInitialLetter();
            var otherLetter = generateInitialLetter();
            enqueue(testId, letter);

            // when / then
            assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(otherLetter), l -> l, null).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(NoSuchDeadLetterException.class);
        }

        @Test
        void requeueReentersLetterToQueueWithUpdatedLastTouchedAndCause() {
            // given
            Object testId = generateId();
            var letter = generateInitialLetter();
            Throwable testCause = generateThrowable();
            enqueue(testId, letter);
            DeadLetter<? extends M> resultLetter = testSubject.deadLetterSequence(testId, null).join().iterator().next();
            DeadLetter<M> expectedLetter = generateRequeuedLetter(letter, testCause);

            // when
            testSubject.requeue(resultLetter, l -> l.withCause(testCause), null).join();

            // then
            assertTrue(testSubject.contains(testId, null).join());
            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertTrue(resultLetters.hasNext());
            DeadLetter<? extends M> requeuedLetter = resultLetters.next();
            assertLetter(expectedLetter, requeuedLetter);
            assertContext(letter.context(), requeuedLetter.context());
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
            assertFalse(testSubject.contains(testId, null).join());
            enqueue(testId, generateInitialLetter());
            assertTrue(testSubject.contains(testId, null).join());
            assertFalse(testSubject.contains(otherTestId, null).join());
        }

        @Test
        void deadLetterSequenceReturnsEnqueuedLettersMatchingGivenSequenceIdentifier() {
            // given
            Object testId = generateId();
            var expected = generateInitialLetter();

            // when / then
            Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertFalse(resultIterator.hasNext());

            enqueue(testId, expected);

            resultIterator = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertTrue(resultIterator.hasNext());
            assertLetter(expected, resultIterator.next());
            assertFalse(resultIterator.hasNext());
        }

        @Test
        void deadLetterSequenceReturnsMatchingEnqueuedLettersInInsertOrder() {
            // given
            Object testId = generateId();
            LinkedHashMap<Integer, DeadLetter<M>> enqueuedLetters = new LinkedHashMap<>();
            var initial = generateInitialLetter();
            enqueue(testId, initial);
            enqueuedLetters.put(0, initial);

            IntStream.range(1, Long.valueOf(maxSequenceSize()).intValue())
                     .forEach(i -> {
                         var followUp = generateFollowUpLetter();
                         enqueue(testId, followUp);
                         enqueuedLetters.put(i, followUp);
                     });

            // when
            Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId, null).join().iterator();

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

            var thisFirstExpected = generateInitialLetter();
            var thisSecondExpected = generateInitialLetter();
            var thatFirstExpected = generateInitialLetter();
            var thatSecondExpected = generateInitialLetter();

            enqueue(thisTestId, thisFirstExpected);
            enqueue(thatTestId, thatFirstExpected);
            enqueue(thisTestId, thisSecondExpected);
            enqueue(thatTestId, thatSecondExpected);

            // when
            Iterator<Iterable<DeadLetter<? extends M>>> result = testSubject.deadLetters(null).join().iterator();

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
            assertFalse(testSubject.isFull(generateId(), null).join());
            long maxSequences = maxSequences();
            assertTrue(maxSequences > 0);
            for (int i = 0; i < maxSequences; i++) {
                enqueue(generateId(), generateInitialLetter());
            }

            // when / then
            assertTrue(testSubject.isFull(generateId(), null).join());
        }

        @Test
        void isFullReturnsTrueAfterMaximumSequenceSizeIsReached() {
            // given
            Object testId = generateId();
            assertFalse(testSubject.isFull(testId, null).join());
            long maxSequenceSize = maxSequenceSize();
            assertTrue(maxSequenceSize > 0);
            for (int i = 0; i < maxSequenceSize; i++) {
                enqueue(testId, generateInitialLetter());
            }

            // when / then
            assertTrue(testSubject.isFull(testId, null).join());
        }

        @Test
        void sizeReturnsOverallNumberOfContainedDeadLetters() {
            // given / when / then
            assertEquals(0, testSubject.size(null).join());

            Object testId = generateId();
            enqueue(testId, generateInitialLetter());
            assertEquals(1, testSubject.size(null).join());
            enqueue(testId, generateInitialLetter());
            assertEquals(2, testSubject.size(null).join());

            enqueue(generateId(), generateInitialLetter());
            assertEquals(3, testSubject.size(null).join());
        }

        @Test
        void sequenceSizeForSequenceIdentifierReturnsTheNumberOfContainedLettersForGivenSequenceIdentifier() {
            // given / when / then
            assertEquals(0, testSubject.sequenceSize("some-id", null).join());

            Object testId = generateId();
            enqueue(testId, generateInitialLetter());
            assertEquals(0, testSubject.sequenceSize("some-id", null).join());
            assertEquals(1, testSubject.sequenceSize(testId, null).join());
            enqueue(testId, generateInitialLetter());
            assertEquals(2, testSubject.sequenceSize(testId, null).join());

            enqueue(generateId(), generateInitialLetter());
            assertEquals(0, testSubject.sequenceSize("some-id", null).join());
            assertEquals(2, testSubject.sequenceSize(testId, null).join());
        }

        @Test
        void amountOfSequencesReturnsTheNumberOfUniqueSequences() {
            // given / when / then
            assertEquals(0, testSubject.amountOfSequences(null).join());

            enqueue(generateId(), generateInitialLetter());
            assertEquals(1, testSubject.amountOfSequences(null).join());

            enqueue(generateId(), generateInitialLetter());
            assertEquals(2, testSubject.amountOfSequences(null).join());

            Object testId = generateId();
            enqueue(testId, generateInitialLetter());
            enqueue(testId, generateInitialLetter());
            enqueue(testId, generateInitialLetter());
            assertEquals(3, testSubject.amountOfSequences(null).join());
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
            boolean result = testSubject.process(testTask, null).join();

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
            var letter = generateInitialLetter();
            enqueue(testId, letter);

            // when
            boolean result = testSubject.process(testTask, null).join();

            // then
            assertTrue(result);
            assertLetter(letter, resultLetter.get());

            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).join().iterator();
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
            var letter = generateInitialLetter();
            enqueue(testId, letter);

            Instant expectedLastTouched = setAndGetTime();
            DeadLetter<M> expectedRequeuedLetter =
                    generateRequeuedLetter(letter, expectedLastTouched, testThrowable, testDiagnostics);

            // when
            boolean result = testSubject.process(testTask, null).join();

            // then
            assertFalse(result);
            assertLetter(letter, resultLetter.get());

            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertTrue(resultLetters.hasNext());
            DeadLetter<? extends M> requeuedLetter = resultLetters.next();
            assertLetter(expectedRequeuedLetter, requeuedLetter);
            assertContext(letter.context(), requeuedLetter.context());
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
            var testThisLetter = generateInitialLetter();
            enqueue(testThisId, testThisLetter);

            setAndGetTime(Instant.now().plus(5, ChronoUnit.SECONDS));
            Object testThatId = generateId();
            var testThatLetter = generateInitialLetter();
            enqueue(testThatId, testThatLetter);

            // when / then
            boolean result = testSubject.process(testTask, null).join();
            assertTrue(result);
            assertLetter(testThisLetter, resultLetter.get());

            result = testSubject.process(testTask, null).join();
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
            var firstTestLetter = generateInitialLetter();
            enqueue(testId, firstTestLetter);
            setAndGetTime(Instant.now());
            var secondTestLetter = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId,
                                         () -> secondTestLetter,
                                         toProcessingContext(secondTestLetter.context()))
                       .join();
            setAndGetTime(Instant.now());
            var thirdTestLetter = generateFollowUpLetter();
            testSubject.enqueueIfPresent(testId,
                                         () -> thirdTestLetter,
                                         toProcessingContext(thirdTestLetter.context()))
                       .join();

            // Advance time so the extra sequence has a later timestamp and won't be processed first
            setAndGetTime(Instant.now().plus(1, ChronoUnit.HOURS));
            enqueue(generateId(), generateInitialLetter());

            // when
            boolean result = testSubject.process(testTask, null).join();

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
            var firstTestLetter = generateInitialLetter();
            enqueue(testId, firstTestLetter);

            List<DeadLetter<M>> expectedOrderList = new LinkedList<>();
            long loopSize = maxSequences() - 5;
            for (int i = 0; i < loopSize; i++) {
                var deadLetter = generateFollowUpLetter();
                expectedOrderList.add(deadLetter);
                testSubject.enqueueIfPresent(testId,
                                             () -> deadLetter,
                                             toProcessingContext(deadLetter.context()))
                           .join();
            }

            // when
            boolean result = testSubject.process(testTask, null).join();

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
            var letter = generateInitialLetter();
            enqueue(testId, letter);

            // when
            Thread blockingProcess = new Thread(() -> testSubject.process(blockingTask, null).join());
            blockingProcess.start();
            assertTrue(isBlocking.await(100, TimeUnit.MILLISECONDS));

            boolean result = testSubject.process(nonBlockingTask, null).join();

            // then
            assertFalse(result);
            assertFalse(invoked.get());

            hasProcessed.countDown();
            blockingProcess.join();
            assertLetter(letter, resultLetter.get());
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

            enqueue(generateId(), generateInitialLetter());
            enqueue(generateId(), generateInitialLetter());

            // when
            boolean result = testSubject.process(letter -> false, testTask, null).join();

            // then
            assertFalse(result);
            assertFalse(releasedLetter.get());
        }

        @Test
        void processWithNoMatchingSequencesReturnsFalseAndPreservesLetters() {
            // given
            AtomicInteger taskInvocations = new AtomicInteger(0);
            Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> testTask = letter -> {
                taskInvocations.incrementAndGet();
                return CompletableFuture.completedFuture(Decisions.evict());
            };

            Object idOne = generateId();
            Object idTwo = generateId();
            enqueue(idOne, generateInitialLetter());
            enqueue(idTwo, generateInitialLetter());

            // when
            // Filter rejects all letters - no sequences match
            boolean result = testSubject.process(letter -> false, testTask, null).join();

            // then
            assertFalse(result);
            assertEquals(0, taskInvocations.get());
            // Verify letters are still in the queue (not evicted)
            assertTrue(testSubject.contains(idOne, null).join());
            assertTrue(testSubject.contains(idTwo, null).join());
            assertEquals(2, testSubject.amountOfSequences(null).join());
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
            var testThisLetter = generateInitialLetter();
            enqueue(testThisId, testThisLetter);

            Object testThatId = generateId();
            var testThatLetter = generateInitialLetter();
            enqueue(testThatId, testThatLetter);

            // when / then
            boolean result = testSubject.process(letterMatches(testThisLetter), testTask, null).join();
            assertTrue(result);
            assertLetter(testThisLetter, resultLetter.get());

            result = testSubject.process(letterMatches(testThatLetter), testTask, null).join();
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
            var testLetter = generateInitialLetter();
            enqueue(testId, testLetter);
            enqueue(nonMatchingId, generateInitialLetter());

            // when
            boolean result = testSubject.process(
                    letterMatches(testLetter),
                    testTask,
                    null
            ).join();

            // then
            assertTrue(result);
            assertLetter(testLetter, resultLetter.get());

            Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId, null).join().iterator();
            assertFalse(resultLetters.hasNext());
            assertTrue(testSubject.deadLetters(null).join().iterator().hasNext());
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

            assertTrue(testSubject.contains(idOne, null).join());
            assertTrue(testSubject.contains(idTwo, null).join());
            assertTrue(testSubject.contains(idThree, null).join());

            // when
            testSubject.clear(null).join();

            // then
            assertFalse(testSubject.contains(idOne, null).join());
            assertFalse(testSubject.contains(idTwo, null).join());
            assertFalse(testSubject.contains(idThree, null).join());
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
     * Generate an initial {@link DeadLetter} for testing. The letter may carry {@link Context} resources
     * (e.g. tracking token, aggregate info) needed by the queue implementation during enqueueing.
     *
     * @return A {@link DeadLetter} with the initial dead letter, including any context resources.
     */
    protected abstract DeadLetter<M> generateInitialLetter();

    /**
     * Generate a follow-up {@link DeadLetter} for testing. The letter may carry {@link Context} resources
     * (e.g. tracking token, aggregate info) needed by the queue implementation during enqueueing.
     *
     * @return A {@link DeadLetter} with the follow-up dead letter, including any context resources.
     */
    protected abstract DeadLetter<M> generateFollowUpLetter();

    /**
     * Generates a {@link DeadLetter} implementation specific to the {@link SequencedDeadLetterQueue} tested, using
     * the provided {@link DeadLetter}.
     * <p>
     * By default, simply returns the letter as-is. Subclasses backed by a database should override this to wrap
     * the letter in the queue-specific implementation (e.g. {@code JpaDeadLetter}, {@code JdbcDeadLetter}).
     *
     * @param letter The dead letter to map.
     * @return The converted dead letter.
     */
    protected DeadLetter<M> mapToQueueImplementation(DeadLetter<M> letter) {
        return letter;
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
        assertMessage(expected.message(), actual.message());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(expected.enqueuedAt(), actual.enqueuedAt());
        assertEquals(expected.lastTouched(), actual.lastTouched());
        assertEquals(expected.diagnostics(), actual.diagnostics());
    }

    /**
     * Assert whether the {@code expected} {@link Message} matches the {@code actual} {@code Message}.
     * <p>
     * By default this compares identifier, type, metadata, and payload directly.
     * Subclasses may override this to handle implementations where the payload requires deserialization before
     * comparison (e.g. when payloads are stored as raw bytes).
     *
     * @param expected The expected message.
     * @param actual   The actual message.
     */
    protected void assertMessage(M expected, M actual) {
        assertEquals(expected.identifier(), actual.identifier());
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.metadata(), actual.metadata());
        assertEquals(expected.payload(), actual.payload());
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
}

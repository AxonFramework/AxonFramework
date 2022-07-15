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

import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract class providing a generic test suite every {@link DeadLetterQueue} implementation should comply with.
 *
 * @param <I> The {@link QueueIdentifier} implementation used to enqueue letters by this test class.
 * @param <M> The {@link Message} implementation enqueued by this test class.
 * @author Steven van Beelen
 */
public abstract class DeadLetterQueueTest<I extends QueueIdentifier, M extends Message<?>> {

    private DeadLetterQueue<M> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = buildTestSubject();
    }

    /**
     * Constructs the {@link DeadLetterQueue} implementation under test.
     *
     * @return A {@link DeadLetterQueue} implementation under test.
     */
    abstract DeadLetterQueue<M> buildTestSubject();

    /**
     * Constructs a {@link QueueIdentifier} implementation expected by the test subject.
     *
     * @return A {@link QueueIdentifier} implementation expected by the test subject.
     */
    abstract I generateQueueId();

    /**
     * Constructs a {@link QueueIdentifier} implementation expected by the test subject.
     *
     * @return A {@link QueueIdentifier} implementation expected by the test subject.
     */
    abstract I generateQueueId(String group);

    /**
     * Constructs a {@link Message} implementation expected by the test subject.
     *
     * @return A {@link Message} implementation expected by the test subject.
     */
    abstract M generateMessage();

    /**
     * Set the {@link Clock} used by this test. Use this to influence the {@link DeadLetter#enqueuedAt()} and
     * {@link DeadLetter#expiresAt()} times.
     *
     * @param clock The clock to use during testing.
     */
    abstract void setClock(Clock clock);

    /**
     * Retrieve expiry threshold used by the {@link DeadLetterQueue} under test. The threshold dictates the
     * {@link DeadLetter#expiresAt()}.
     *
     * @return The expiry threshold used by the {@link DeadLetterQueue} under test.
     */
    abstract Duration expireThreshold();

    @Test
    void testEnqueue() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testDeadLetter = generateMessage();
        Throwable testThrowable = generateThrowable();

        DeadLetter<M> result = testSubject.enqueue(testId, testDeadLetter, testThrowable);

        assertTrue(testSubject.contains(testId));
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testDeadLetter, result.message());
        assertEquals(expectedCause(testThrowable), result.cause());
        assertEquals(expectedDeadLettered, result.enqueuedAt());
        assertEquals(expectedExpireAt, result.expiresAt());
        assertEquals(0, result.numberOfRetries());
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxQueuesIsReached() {
        long maxQueues = testSubject.maxQueues();
        assertTrue(maxQueues > 0);

        for (int i = 0; i < maxQueues; i++) {
            testSubject.enqueue(generateQueueId(), generateMessage(), generateThrowable());
        }

        assertThrows(
                DeadLetterQueueOverflowException.class,
                () -> testSubject.enqueue(generateQueueId(), generateMessage(), generateThrowable())
        );
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxQueueSizeIsReached() {
        I testId = generateQueueId();

        long maxQueueSize = testSubject.maxQueueSize();
        assertTrue(maxQueueSize > 0);

        for (int i = 0; i < maxQueueSize; i++) {
            testSubject.enqueue(testId, generateMessage(), generateThrowable());
        }

        assertThrows(
                DeadLetterQueueOverflowException.class,
                () -> testSubject.enqueue(testId, generateMessage(), generateThrowable())
        );
    }

    @Test
    void testOnAvailableCallbackIsInvokedAfterConfiguredExpireThresholdForAnEnqueuedLetter() {
        Duration testExpireThreshold = expireThreshold();
        I testId = generateQueueId();
        M testLetter = generateMessage();
        Throwable testThrowable = generateThrowable();

        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(testExpireThreshold);
        CountDownLatch expectedCallbackInvocations = new CountDownLatch(1);

        // Set the callback that's invoked after the expiry threshold is reached that's followed by enqueue.
        testSubject.onAvailable(testId.group(), expectedCallbackInvocations::countDown);

        DeadLetter<M> result = testSubject.enqueue(testId, testLetter, testThrowable);

        assertTrue(testSubject.contains(testId));
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testLetter, result.message());
        assertEquals(expectedCause(testThrowable), result.cause());
        assertEquals(expectedDeadLettered, result.enqueuedAt());
        assertEquals(expectedExpireAt, result.expiresAt());
        assertEquals(0, result.numberOfRetries());

        try {
            assertTrue(expectedCallbackInvocations.await(testExpireThreshold.toMillis() * 2, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("Callback should have been invoked within [" + testExpireThreshold + "]");
        }
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForEmptyQueue() {
        I testQueueId = generateQueueId();

        Optional<DeadLetter<M>> result = testSubject.enqueueIfPresent(testQueueId, generateMessage());

        assertFalse(result.isPresent());
        assertFalse(testSubject.contains(testQueueId));
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForNonExistentQueueIdentifier() {
        I testFirstId = generateQueueId();
        Throwable testThrowable = generateThrowable();

        testSubject.enqueue(testFirstId, generateMessage(), testThrowable);

        I testSecondId = generateQueueId();

        Optional<DeadLetter<M>> result = testSubject.enqueueIfPresent(testSecondId, generateMessage());

        assertFalse(result.isPresent());
        assertTrue(testSubject.contains(testFirstId));
        assertFalse(testSubject.contains(testSecondId));
    }

    @Test
    void testEnqueueIfPresentEnqueuesForExistingQueueIdentifier() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        Throwable testThrowable = generateThrowable();

        // First dead letter inserted...
        M testFirstLetter = generateMessage();
        DeadLetter<M> enqueueResult = testSubject.enqueue(testId, testFirstLetter, testThrowable);
        // Second dead letter inserted...
        M testSecondLetter = generateMessage();
        testSubject.enqueueIfPresent(testId, testSecondLetter);

        assertTrue(testSubject.contains(testId));
        assertEquals(testId, enqueueResult.queueIdentifier());
        assertEquals(testFirstLetter, enqueueResult.message());
        assertEquals(expectedCause(testThrowable), enqueueResult.cause());
        assertEquals(expectedDeadLettered, enqueueResult.enqueuedAt());
        assertEquals(expectedExpireAt, enqueueResult.expiresAt());
        assertEquals(0, enqueueResult.numberOfRetries());

        // Let the letters expire in the queue by move the clock to after the expected expiry time.
        setAndGetTime(expectedExpireAt.plusMillis(1));

        Optional<DeadLetter<M>> takenResult = testSubject.take(testId.group());
        assertTrue(takenResult.isPresent());
        assertEquals(enqueueResult, takenResult.get());
    }

    @Test
    void testOnAvailableCallbackIsInvokedAfterConfiguredExpireThresholdForAnEnqueuedIfPresentLetter() {
        Duration testExpireThreshold = expireThreshold();
        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        M testSecondLetter = generateMessage();
        Throwable testThrowable = generateThrowable();

        Instant expectedFirstDeadLettered = setAndGetTime();
        Instant expectedFirstExpireAt = expectedFirstDeadLettered.plus(testExpireThreshold);
        CountDownLatch expectedCallbackInvocations = new CountDownLatch(2);

        // Set the callback that's invoked after the expiry threshold is reached that's followed by enqueue.
        testSubject.onAvailable(testId.group(), expectedCallbackInvocations::countDown);

        DeadLetter<M> enqueueResult = testSubject.enqueue(testId, testFirstLetter, testThrowable);

        assertTrue(testSubject.contains(testId));
        assertEquals(testId, enqueueResult.queueIdentifier());
        assertEquals(testFirstLetter, enqueueResult.message());
        assertEquals(expectedCause(testThrowable), enqueueResult.cause());
        assertEquals(expectedFirstDeadLettered, enqueueResult.enqueuedAt());
        assertEquals(expectedFirstExpireAt, enqueueResult.expiresAt());
        assertEquals(0, enqueueResult.numberOfRetries());

        Instant expectedSecondDeadLettered = setAndGetTime(expectedFirstExpireAt);
        Optional<DeadLetter<M>> optionalResult = testSubject.enqueueIfPresent(testId, testSecondLetter);

        assertTrue(optionalResult.isPresent());
        DeadLetter<M> enqueueIfPresentResult = optionalResult.get();
        assertEquals(testId, enqueueIfPresentResult.queueIdentifier());
        assertEquals(testSecondLetter, enqueueIfPresentResult.message());
        assertNull(enqueueIfPresentResult.cause());
        assertEquals(expectedSecondDeadLettered, enqueueIfPresentResult.enqueuedAt());
        // The expiresAt equals the deadLettered time whenever the message is enqueue due to a contained earlier letter.
        assertEquals(expectedSecondDeadLettered, enqueueIfPresentResult.expiresAt());
        assertEquals(0, enqueueIfPresentResult.numberOfRetries());

        try {
            assertTrue(expectedCallbackInvocations.await(testExpireThreshold.toMillis() * 2, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("Callback should have been invoked within [" + testExpireThreshold + "]");
        }
    }

    @Test
    void testContains() {
        I testId = generateQueueId();
        I otherTestId = generateQueueId();
        I otherTestIdWithSameGroup = generateQueueId(testId.group());

        assertFalse(testSubject.contains(testId));

        testSubject.enqueue(testId, generateMessage(), generateThrowable());

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.contains(otherTestId));
        assertFalse(testSubject.contains(otherTestIdWithSameGroup));
    }

    @Test
    void testDeadLetters() {
        I testId = generateQueueId();

        Iterator<DeadLetter<M>> resultIterator = testSubject.deadLetters(testId).iterator();
        assertFalse(resultIterator.hasNext());

        DeadLetter<M> expected = testSubject.enqueue(testId, generateMessage(), generateThrowable());

        resultIterator = testSubject.deadLetters(testId).iterator();
        assertTrue(resultIterator.hasNext());
        assertEquals(expected, resultIterator.next());
        assertFalse(resultIterator.hasNext());
    }

    @Test
    void testDeadLetterSequences() {
        I thisTestId = generateQueueId("first");
        I thatTestId = generateQueueId("second");
        int numberOfSequences = 2;

        Iterator<DeadLetterSequence<M>> sequenceIterator = testSubject.deadLetterSequences().iterator();
        assertFalse(sequenceIterator.hasNext());

        DeadLetter<M> thisFirstExpected = testSubject.enqueue(thisTestId, generateMessage(), generateThrowable());
        DeadLetter<M> thatFirstExpected = testSubject.enqueue(thatTestId, generateMessage(), generateThrowable());
        DeadLetter<M> thisSecondExpected = testSubject.enqueue(thisTestId, generateMessage(), generateThrowable());
        DeadLetter<M> thatSecondExpected = testSubject.enqueue(thatTestId, generateMessage(), generateThrowable());

        sequenceIterator = testSubject.deadLetterSequences().iterator();

        for (int i = 0; i < numberOfSequences; i++) {
            assertTrue(sequenceIterator.hasNext());
            DeadLetterSequence<M> resultSequence = sequenceIterator.next();
            QueueIdentifier resultQueueId = resultSequence.queueIdentifier();
            if (Objects.equals(thisTestId, resultQueueId)) {
                Iterator<DeadLetter<M>> thisSequenceIterator = resultSequence.sequence().iterator();
                assertTrue(thisSequenceIterator.hasNext());
                assertEquals(thisFirstExpected, thisSequenceIterator.next());
                assertTrue(thisSequenceIterator.hasNext());
                assertEquals(thisSecondExpected, thisSequenceIterator.next());
                assertFalse(thisSequenceIterator.hasNext());
            } else if (Objects.equals(thatTestId, resultQueueId)) {
                Iterator<DeadLetter<M>> thatSequenceIterator = resultSequence.sequence().iterator();
                assertTrue(thatSequenceIterator.hasNext());
                assertEquals(thatFirstExpected, thatSequenceIterator.next());
                assertTrue(thatSequenceIterator.hasNext());
                assertEquals(thatSecondExpected, thatSequenceIterator.next());
                assertFalse(thatSequenceIterator.hasNext());
            } else {
                fail("The sequences contained an unexpected queue identifier [" + resultQueueId + "]");
            }
        }
        assertFalse(sequenceIterator.hasNext());
    }

    @Test
    void testIsFullReturnsTrueAfterMaximumAmountOfQueuesIsReached() {
        assertFalse(testSubject.isFull(generateQueueId()));

        long maxQueues = testSubject.maxQueues();
        assertTrue(maxQueues > 0);

        for (int i = 0; i < maxQueues; i++) {
            testSubject.enqueue(generateQueueId(), generateMessage(), generateThrowable());
        }

        assertTrue(testSubject.isFull(generateQueueId()));
    }

    @Test
    void testIsFullReturnsTrueAfterMaximumQueueSizeIsReached() {
        I testId = generateQueueId();

        assertFalse(testSubject.isFull(testId));

        long maxQueueSize = testSubject.maxQueueSize();
        assertTrue(maxQueueSize > 0);

        for (int i = 0; i < maxQueueSize; i++) {
            testSubject.enqueue(testId, generateMessage(), generateThrowable());
        }

        assertTrue(testSubject.isFull(testId));
    }

    @Test
    void testTake() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        Throwable testThrowable = generateThrowable();
        M testSecondLetter = generateMessage();

        testSubject.enqueue(testId, testFirstLetter, testThrowable);
        testSubject.enqueue(testId, testSecondLetter, testThrowable);

        // Let the letters expire in the queue by move the clock to after the expected expiry time.
        setAndGetTime(expectedExpireAt.plusMillis(1));

        Optional<DeadLetter<M>> firstOptionalResult = testSubject.take(testId.group());
        assertTrue(firstOptionalResult.isPresent());
        DeadLetter<M> firstResult = firstOptionalResult.get();
        assertEquals(testId, firstResult.queueIdentifier());
        assertEquals(testFirstLetter, firstResult.message());
        assertEquals(expectedCause(testThrowable), firstResult.cause());
        assertEquals(expectedDeadLettered, firstResult.enqueuedAt());
        assertEquals(expectedExpireAt, firstResult.expiresAt());
        assertEquals(0, firstResult.numberOfRetries());
        firstResult.acknowledge();

        Optional<DeadLetter<M>> secondOptionalResult = testSubject.take(testId.group());
        assertTrue(secondOptionalResult.isPresent());
        DeadLetter<M> secondResult = secondOptionalResult.get();
        assertEquals(testId, secondResult.queueIdentifier());
        assertEquals(testSecondLetter, secondResult.message());
        assertEquals(expectedCause(testThrowable), secondResult.cause());
        assertEquals(expectedDeadLettered, secondResult.enqueuedAt());
        assertEquals(expectedExpireAt, secondResult.expiresAt());
        assertEquals(0, secondResult.numberOfRetries());
        // Only one letter was acknowledged, so the second still remains.
        assertTrue(testSubject.contains(testId));
    }

    @Test
    void testTakeOnEmptyQueueReturnsEmptyOptional() {
        String testGroup = "some-group";

        assertFalse(testSubject.take(testGroup).isPresent());
    }

    @Test
    void testTakeForNonContainedGroupReturnsEmptyOptional() {
        I testId = generateQueueId();
        String testGroup = "some-group";
        assertNotEquals(testGroup, testId.group());

        assertFalse(testSubject.contains(testId));

        testSubject.enqueue(testId, generateMessage(), generateThrowable());
        testSubject.enqueue(testId, generateMessage(), generateThrowable());
        testSubject.enqueue(testId, generateMessage(), generateThrowable());

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.take(testGroup).isPresent());
    }

    @Test
    void testTakeReturnsDeadLettersInInsertOrder() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testThisId = generateQueueId();
        M testThisFirstLetter = generateMessage();
        Throwable testThisThrowable = generateThrowable();
        M testThisSecondLetter = generateMessage();

        testSubject.enqueue(testThisId, testThisFirstLetter, testThisThrowable);
        testSubject.enqueueIfPresent(testThisId, testThisSecondLetter);

        I testThatId = generateQueueId();
        M testThatFirstLetter = generateMessage();
        Throwable testThatThrowable = generateThrowable();
        M testThatSecondLetter = generateMessage();

        testSubject.enqueue(testThatId, testThatFirstLetter, testThatThrowable);
        testSubject.enqueueIfPresent(testThatId, testThatSecondLetter);

        // Let the letters expire in the queue by move the clock to after the expected expiry time.
        setAndGetTime(expectedExpireAt.plusMillis(1));

        Optional<DeadLetter<M>> thisOptionalFirstResult = testSubject.take(testThisId.group());
        assertTrue(thisOptionalFirstResult.isPresent());
        DeadLetter<M> thisFirstResult = thisOptionalFirstResult.get();
        assertEquals(testThisId, thisFirstResult.queueIdentifier());
        assertEquals(testThisFirstLetter, thisFirstResult.message());
        assertEquals(expectedCause(testThisThrowable), thisFirstResult.cause());
        assertEquals(expectedDeadLettered, thisFirstResult.enqueuedAt());
        assertEquals(expectedExpireAt, thisFirstResult.expiresAt());
        assertEquals(0, thisFirstResult.numberOfRetries());
        thisFirstResult.acknowledge();

        Optional<DeadLetter<M>> thisOptionalSecondResult = testSubject.take(testThisId.group());
        assertTrue(thisOptionalSecondResult.isPresent());
        DeadLetter<M> thisSecondResult = thisOptionalSecondResult.get();
        assertEquals(testThisId, thisSecondResult.queueIdentifier());
        assertEquals(testThisSecondLetter, thisSecondResult.message());
        assertNull(thisSecondResult.cause());
        assertEquals(expectedDeadLettered, thisSecondResult.enqueuedAt());
        // The expiresAt equals the deadLettered time whenever the message is enqueue due to a contained earlier letter.
        assertEquals(expectedDeadLettered, thisSecondResult.expiresAt());
        assertEquals(0, thisSecondResult.numberOfRetries());
        thisSecondResult.acknowledge();

        Optional<DeadLetter<M>> thatOptionalFirstResult = testSubject.take(testThatId.group());
        assertTrue(thatOptionalFirstResult.isPresent());
        DeadLetter<M> thatFirstResult = thatOptionalFirstResult.get();
        assertEquals(testThatId, thatFirstResult.queueIdentifier());
        assertEquals(testThatFirstLetter, thatFirstResult.message());
        assertEquals(expectedCause(testThatThrowable), thatFirstResult.cause());
        assertEquals(expectedDeadLettered, thatFirstResult.enqueuedAt());
        assertEquals(expectedExpireAt, thatFirstResult.expiresAt());
        assertEquals(0, thatFirstResult.numberOfRetries());
        thatFirstResult.acknowledge();
        // The second 'that' letter is still in the queue.
        assertTrue(testSubject.contains(testThatId));
    }

    /**
     * An "active sequence" in this case means that a letter with {@link QueueIdentifier} {@code x} is not
     * {@link DeadLetter#acknowledge() acknowledged} or {@link DeadLetter#requeue() requeued} yet. Furthermore, if it's
     * the sole letter for that {@code group}, nothing should be returned. This approach ensure the events for a given
     * {@link QueueIdentifier} are handled in the order they've been dead-lettered (a.k.a., in sequence).
     */
    @Test
    void testTakeReturnsEmptyOptionalForAnActiveSequenceAndOtherwiseEmptyQueue() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        Throwable testThisThrowable = generateThrowable();
        M testSecondLetter = generateMessage();

        testSubject.enqueue(testId, testFirstLetter, testThisThrowable);
        testSubject.enqueueIfPresent(testId, testSecondLetter);

        // Let the letters expire in the queue by move the clock to after the expected expiry time.
        setAndGetTime(expectedExpireAt.plusMillis(1));

        Optional<DeadLetter<M>> firstOptionalResult = testSubject.take(testId.group());
        assertTrue(firstOptionalResult.isPresent());
        DeadLetter<M> firstResult = firstOptionalResult.get();
        assertEquals(testId, firstResult.queueIdentifier());
        assertEquals(testFirstLetter, firstResult.message());
        assertEquals(expectedCause(testThisThrowable), firstResult.cause());
        assertEquals(expectedDeadLettered, firstResult.enqueuedAt());
        assertEquals(expectedExpireAt, firstResult.expiresAt());
        assertEquals(0, firstResult.numberOfRetries());
        // No DeadLetter#acknowledge or DeadLetter#requeue invocation here, as that releases the sequence.
        assertFalse(testSubject.take(testId.group()).isPresent());
        assertTrue(testSubject.contains(testId));
    }

    @Test
    void testTakeReturnsEmptyOptionalForNotExpiredEntries() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testLetter = generateMessage();
        Throwable testThrowable = generateThrowable();

        testSubject.enqueue(testId, testLetter, testThrowable);

        // Set the test's time right before the expireAt time.
        setAndGetTime(expectedDeadLettered.plus(expireThreshold().minusMillis(50)));

        Optional<DeadLetter<M>> optionalResult = testSubject.take(testId.group());
        assertFalse(optionalResult.isPresent());

        // Set the test's time after the expireAt time.
        setAndGetTime(expectedDeadLettered.plus(expireThreshold().plusMillis(50)));

        optionalResult = testSubject.take(testId.group());
        assertTrue(optionalResult.isPresent());
        DeadLetter<M> result = optionalResult.get();
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testLetter, result.message());
        assertEquals(expectedCause(testThrowable), result.cause());
        assertEquals(expectedDeadLettered, result.enqueuedAt());
        assertEquals(expectedExpireAt, result.expiresAt());
        assertEquals(0, result.numberOfRetries());
        result.acknowledge();
        assertFalse(testSubject.contains(testId));
    }

    @Test
    void testAcknowledgeRemovesLetterFromQueue() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testLetter = generateMessage();
        Throwable testThrowable = generateThrowable();

        testSubject.enqueue(testId, testLetter, testThrowable);
        assertTrue(testSubject.contains(testId));

        // Let the letters expire in the queue by move the clock to after the expected expiry time.
        setAndGetTime(expectedExpireAt.plusMillis(1));

        Optional<DeadLetter<M>> optionalResult = testSubject.take(testId.group());
        assertTrue(optionalResult.isPresent());
        DeadLetter<M> result = optionalResult.get();
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testLetter, result.message());
        assertEquals(expectedCause(testThrowable), result.cause());
        assertEquals(expectedDeadLettered, result.enqueuedAt());
        assertEquals(expectedExpireAt, result.expiresAt());
        assertEquals(0, result.numberOfRetries());

        // Evaluation of the dead-letter was successful, so its acknowledged and thus removed from the queue.
        result.acknowledge();

        assertFalse(testSubject.contains(testId));
        assertFalse(testSubject.take(testId.group()).isPresent());
    }

    @Test
    void testRequeueReenterLetterToQueueWithAdjustedExpireAtAndNumberOfRetries() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testLetter = generateMessage();
        Throwable testThrowable = generateThrowable();

        testSubject.enqueue(testId, testLetter, testThrowable);
        assertTrue(testSubject.contains(testId));

        // Let the letters expire in the queue by move the clock to after the expected expiry time.
        setAndGetTime(expectedExpireAt.plusMillis(1));

        Optional<DeadLetter<M>> optionalFirstTryResult = testSubject.take(testId.group());
        assertTrue(optionalFirstTryResult.isPresent());
        DeadLetter<M> firstTryResult = optionalFirstTryResult.get();
        assertEquals(testId, firstTryResult.queueIdentifier());
        assertEquals(testLetter, firstTryResult.message());
        assertEquals(expectedCause(testThrowable), firstTryResult.cause());
        assertEquals(expectedDeadLettered, firstTryResult.enqueuedAt());
        assertEquals(expectedExpireAt, firstTryResult.expiresAt());
        assertEquals(0, firstTryResult.numberOfRetries());

        Instant expectedUpdatedExpireAt = setAndGetTime().plus(expireThreshold());
        // Evaluation of the dead-letter was unsuccessful, so its requeued and thus kept in the queue.
        firstTryResult.requeue();

        // Move time again, as requeue changed the expiry time.
        setAndGetTime(expectedUpdatedExpireAt.plusMillis(1));

        assertTrue(testSubject.contains(testId));
        Optional<DeadLetter<M>> optionalSecondTry = testSubject.take(testId.group());
        assertTrue(optionalSecondTry.isPresent());
        DeadLetter<M> secondTryResult = optionalSecondTry.get();
        assertEquals(firstTryResult.queueIdentifier(), secondTryResult.queueIdentifier());
        assertEquals(firstTryResult.message(), secondTryResult.message());
        assertEquals(firstTryResult.cause(), secondTryResult.cause());
        assertEquals(firstTryResult.enqueuedAt(), secondTryResult.enqueuedAt());
        assertEquals(expectedUpdatedExpireAt, secondTryResult.expiresAt());
        assertEquals(1, secondTryResult.numberOfRetries());
    }

    @Test
    void testReleaseUpdatesExpireAtToNow() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(Duration.ofMillis(5000));

        I testThisId = generateQueueId();
        M testThisLetter = generateMessage();
        Throwable testThisThrowable = generateThrowable();
        testSubject.enqueue(testThisId, testThisLetter, testThisThrowable);

        I testThatId = generateQueueId();
        M testThatLetter = generateMessage();
        Throwable testThatThrowable = generateThrowable();
        testSubject.enqueue(testThatId, testThatLetter, testThatThrowable);

        // Set the time to the expected expireAt.
        setAndGetTime(expectedExpireAt);
        // Release all letters.
        testSubject.release();

        // Let the letters expire in the queue by move the clock to after the expected expiry time.
        setAndGetTime(expectedExpireAt.plusMillis(1));

        Optional<DeadLetter<M>> thisOptionalResult = testSubject.take(testThisId.group());
        assertTrue(thisOptionalResult.isPresent());
        DeadLetter<M> thisResult = thisOptionalResult.get();
        assertEquals(testThisId, thisResult.queueIdentifier());
        assertEquals(testThisLetter, thisResult.message());
        assertEquals(expectedCause(testThisThrowable), thisResult.cause());
        assertEquals(expectedDeadLettered, thisResult.enqueuedAt());
        assertEquals(expectedExpireAt, thisResult.expiresAt());
        assertEquals(0, thisResult.numberOfRetries());

        Optional<DeadLetter<M>> thatOptionalResult = testSubject.take(testThatId.group());
        assertTrue(thatOptionalResult.isPresent());
        DeadLetter<M> thatResult = thatOptionalResult.get();
        assertEquals(testThatId, thatResult.queueIdentifier());
        assertEquals(testThatLetter, thatResult.message());
        assertEquals(expectedCause(testThatThrowable), thatResult.cause());
        assertEquals(expectedDeadLettered, thatResult.enqueuedAt());
        assertEquals(expectedExpireAt, thatResult.expiresAt());
        assertEquals(0, thatResult.numberOfRetries());
    }

    @Test
    void testReleaseUpdatesExpireAtToNowForEntriesMatchingTheReleasePredicate() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedOriginalExpireAt = expectedDeadLettered.plus(expireThreshold());
        Instant expectedUpdatedExpireAt = expectedDeadLettered.plus(Duration.ofMillis(5000));

        I testThisId = generateQueueId();
        M testThisLetter = generateMessage();
        Throwable testThisThrowable = generateThrowable();
        testSubject.enqueue(testThisId, testThisLetter, testThisThrowable);

        I testThatId = generateQueueId();
        M testThatLetter = generateMessage();
        Throwable testThatThrowable = generateThrowable();
        testSubject.enqueue(testThatId, testThatLetter, testThatThrowable);

        // Set the time to the expected expireAt.
        setAndGetTime(expectedUpdatedExpireAt);
        // Release letters matching testThatId.
        testSubject.release(queueIdentifier -> queueIdentifier.equals(testThatId));

        Optional<DeadLetter<M>> thisOptionalResult = testSubject.take(testThisId.group());
        assertTrue(thisOptionalResult.isPresent());
        DeadLetter<M> thisResult = thisOptionalResult.get();
        assertEquals(testThisId, thisResult.queueIdentifier());
        assertEquals(testThisLetter, thisResult.message());
        assertEquals(expectedCause(testThisThrowable), thisResult.cause());
        assertEquals(expectedDeadLettered, thisResult.enqueuedAt());
        assertEquals(expectedOriginalExpireAt, thisResult.expiresAt());
        assertEquals(0, thisResult.numberOfRetries());

        // Let the letters expire in the queue by move the clock to after the expected expiry time.
        setAndGetTime(expectedUpdatedExpireAt.plusMillis(1));

        Optional<DeadLetter<M>> thatOptionalResult = testSubject.take(testThatId.group());
        assertTrue(thatOptionalResult.isPresent());
        DeadLetter<M> thatResult = thatOptionalResult.get();
        assertEquals(testThatId, thatResult.queueIdentifier());
        assertEquals(testThatLetter, thatResult.message());
        assertEquals(expectedCause(testThatThrowable), thatResult.cause());
        assertEquals(expectedDeadLettered, thatResult.enqueuedAt());
        assertEquals(expectedUpdatedExpireAt, thatResult.expiresAt());
        assertEquals(0, thatResult.numberOfRetries());
    }

    @Test
    void testReleaseInvokesAvailabilityCallbacksForUpdatedLetters() {
        AtomicBoolean thisExpectedCallbackInvoked = new AtomicBoolean(false);

        I testThisId = generateQueueId();
        M testThisLetter = generateMessage();
        Throwable testThisThrowable = generateThrowable();
        testSubject.enqueue(testThisId, testThisLetter, testThisThrowable);
        testSubject.onAvailable(testThisId.group(), () -> thisExpectedCallbackInvoked.set(true));

        AtomicBoolean thatExpectedCallbackInvoked = new AtomicBoolean(false);

        I testThatId = generateQueueId();
        M testThatLetter = generateMessage();
        Throwable testThatThrowable = generateThrowable();
        testSubject.enqueue(testThatId, testThatLetter, testThatThrowable);
        testSubject.onAvailable(testThatId.group(), () -> thatExpectedCallbackInvoked.set(true));

        // Release letters matching testThisId.
        testSubject.release(queueIdentifier -> queueIdentifier.equals(testThisId));

        assertWithin(100, TimeUnit.MILLISECONDS, () -> assertTrue(thisExpectedCallbackInvoked.get()));
        assertFalse(thatExpectedCallbackInvoked.get());
    }

    @Test
    void testClearRemovesAllEntries() {
        I queueIdOne = generateQueueId();
        I queueIdTwo = generateQueueId();
        I queueIdThree = generateQueueId();

        assertFalse(testSubject.contains(queueIdOne));
        assertFalse(testSubject.contains(queueIdTwo));
        assertFalse(testSubject.contains(queueIdThree));

        testSubject.enqueue(queueIdOne, generateMessage(), generateThrowable());
        testSubject.enqueue(queueIdTwo, generateMessage(), generateThrowable());
        testSubject.enqueue(queueIdThree, generateMessage(), generateThrowable());

        assertTrue(testSubject.contains(queueIdOne));
        assertTrue(testSubject.contains(queueIdTwo));
        assertTrue(testSubject.contains(queueIdThree));

        testSubject.clear();

        assertFalse(testSubject.contains(queueIdOne));
        assertFalse(testSubject.contains(queueIdTwo));
        assertFalse(testSubject.contains(queueIdThree));
    }

    @Test
    void testClearForGroupRemovesAllEntriesOfThatGroup() {
        I thisId = generateQueueId();
        I thatId = generateQueueId();

        assertFalse(testSubject.contains(thisId));
        assertFalse(testSubject.contains(thatId));

        testSubject.enqueue(thisId, generateMessage(), generateThrowable());
        testSubject.enqueue(thisId, generateMessage(), generateThrowable());
        testSubject.enqueue(thatId, generateMessage(), generateThrowable());
        testSubject.enqueue(thatId, generateMessage(), generateThrowable());

        assertTrue(testSubject.contains(thisId));
        assertTrue(testSubject.contains(thatId));

        testSubject.clear(thisId.group());

        assertFalse(testSubject.contains(thisId));
        assertTrue(testSubject.contains(thatId));
    }

    @Test
    void testClearWithPredicateRemovesAllMatchingEntries() {
        I thisId = generateQueueId();
        I thatId = generateQueueId();
        I thirdId = generateQueueId();

        assertFalse(testSubject.contains(thisId));
        assertFalse(testSubject.contains(thatId));
        assertFalse(testSubject.contains(thirdId));

        testSubject.enqueue(thisId, generateMessage(), generateThrowable());
        testSubject.enqueue(thisId, generateMessage(), generateThrowable());
        testSubject.enqueue(thatId, generateMessage(), generateThrowable());
        testSubject.enqueue(thatId, generateMessage(), generateThrowable());
        testSubject.enqueue(thirdId, generateMessage(), generateThrowable());
        testSubject.enqueue(thirdId, generateMessage(), generateThrowable());

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
     * Generate a unique {@link Throwable} by using {@link #generateId()} in the cause description.
     *
     * @return A unique {@link Throwable} by using {@link #generateId()} in the cause description.
     */
    protected static Throwable generateThrowable() {
        return new RuntimeException("Because..." + generateId());
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
     * Constructs the expected {@link Cause} based on the given {@code throwable}.
     *
     * @param throwable A {@link Throwable} to base the {@link Cause} on.
     * @return The expected {@link Cause} based on the given {@code throwable}.
     */
    protected Cause expectedCause(Throwable throwable) {
        return new GenericCause(throwable);
    }
}
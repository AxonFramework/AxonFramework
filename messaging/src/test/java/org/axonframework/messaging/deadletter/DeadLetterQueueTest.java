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
     * Constructs a {@link Message} implementation expected by the test subject.
     *
     * @return A {@link Message} implementation expected by the test subject.
     */
    abstract M generateMessage();

    /**
     * Set the {@link Clock} used by this test. Use this to influence the {@link DeadLetterEntry#deadLettered()} and
     * {@link DeadLetterEntry#expiresAt()} times.
     *
     * @param clock The clock to use during testing.
     */
    abstract void setClock(Clock clock);

    /**
     * Retrieve expiry threshold used by the {@link DeadLetterQueue} under test. The threshold dictates the {@link
     * DeadLetterEntry#expiresAt()}.
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
        Throwable testCause = generateCause();

        DeadLetterEntry<M> result = testSubject.enqueue(testId, testDeadLetter, testCause);

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.isEmpty());

        assertEquals(testId, result.queueIdentifier());
        assertEquals(testDeadLetter, result.message());
        assertEquals(testCause, result.cause());
        assertEquals(expectedDeadLettered, result.deadLettered());
        assertEquals(expectedExpireAt, result.expiresAt());
        assertEquals(0, result.numberOfRetries());
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxQueuesIsReached() {
        long maxQueues = testSubject.maxQueues();
        assertTrue(maxQueues > 0);

        for (int i = 0; i < maxQueues; i++) {
            testSubject.enqueue(generateQueueId(), generateMessage(), generateCause());
        }

        assertThrows(
                DeadLetterQueueOverflowException.class,
                () -> testSubject.enqueue(generateQueueId(), generateMessage(), generateCause())
        );
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxQueueSizeIsReached() {
        I testId = generateQueueId();

        long maxQueueSize = testSubject.maxQueueSize();
        assertTrue(maxQueueSize > 0);

        for (int i = 0; i < maxQueueSize; i++) {
            testSubject.enqueue(testId, generateMessage(), generateCause());
        }

        assertThrows(
                DeadLetterQueueOverflowException.class,
                () -> testSubject.enqueue(testId, generateMessage(), generateCause())
        );
    }

    @Test
    void testOnAvailableCallbackIsInvokedAfterConfiguredExpireThresholdForAnEnqueuedLetter() {
        Duration testExpireThreshold = expireThreshold();
        I testId = generateQueueId();
        M testLetter = generateMessage();
        Throwable testCause = generateCause();

        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(testExpireThreshold);
        CountDownLatch expectedCallbackInvocations = new CountDownLatch(1);

        // Set the callback that's invoked after the expiry threshold is reached that's followed by enqueue.
        testSubject.onAvailable(testId.group(), expectedCallbackInvocations::countDown);

        DeadLetterEntry<M> result = testSubject.enqueue(testId, testLetter, testCause);

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.isEmpty());
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testLetter, result.message());
        assertEquals(testCause, result.cause());
        assertEquals(expectedDeadLettered, result.deadLettered());
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

        Optional<DeadLetterEntry<M>> result = testSubject.enqueueIfPresent(testQueueId, generateMessage());

        assertFalse(result.isPresent());
        assertFalse(testSubject.contains(testQueueId));
        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForNonExistentQueueIdentifier() {
        I testFirstId = generateQueueId();
        Throwable testCause = generateCause();

        testSubject.enqueue(testFirstId, generateMessage(), testCause);

        I testSecondId = generateQueueId();

        Optional<DeadLetterEntry<M>> result = testSubject.enqueueIfPresent(testSecondId, generateMessage());

        assertFalse(result.isPresent());
        assertTrue(testSubject.contains(testFirstId));
        assertFalse(testSubject.contains(testSecondId));
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testEnqueueIfPresentEnqueuesForExistingQueueIdentifier() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        Throwable testCause = generateCause();

        // First dead letter inserted...
        M testFirstLetter = generateMessage();
        DeadLetterEntry<M> enqueueResult = testSubject.enqueue(testId, testFirstLetter, testCause);
        // Second dead letter inserted...
        M testSecondLetter = generateMessage();
        testSubject.enqueueIfPresent(testId, testSecondLetter);

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.isEmpty());

        assertEquals(testId, enqueueResult.queueIdentifier());
        assertEquals(testFirstLetter, enqueueResult.message());
        assertEquals(testCause, enqueueResult.cause());
        assertEquals(expectedDeadLettered, enqueueResult.deadLettered());
        assertEquals(expectedExpireAt, enqueueResult.expiresAt());
        assertEquals(0, enqueueResult.numberOfRetries());

        Optional<DeadLetterEntry<M>> takenResult = testSubject.take(testId.group());
        assertTrue(takenResult.isPresent());
        assertEquals(enqueueResult, takenResult.get());
    }

    @Test
    void testOnAvailableCallbackIsInvokedAfterConfiguredExpireThresholdForAnEnqueuedIfPresentLetter() {
        Duration testExpireThreshold = expireThreshold();
        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        M testSecondLetter = generateMessage();
        Throwable testCause = generateCause();

        Instant expectedFirstDeadLettered = setAndGetTime();
        Instant expectedFirstExpireAt = expectedFirstDeadLettered.plus(testExpireThreshold);
        CountDownLatch expectedCallbackInvocations = new CountDownLatch(2);

        // Set the callback that's invoked after the expiry threshold is reached that's followed by enqueue.
        testSubject.onAvailable(testId.group(), expectedCallbackInvocations::countDown);

        DeadLetterEntry<M> enqueueResult = testSubject.enqueue(testId, testFirstLetter, testCause);

        assertTrue(testSubject.contains(testId));
        assertFalse(testSubject.isEmpty());

        assertEquals(testId, enqueueResult.queueIdentifier());
        assertEquals(testFirstLetter, enqueueResult.message());
        assertEquals(testCause, enqueueResult.cause());
        assertEquals(expectedFirstDeadLettered, enqueueResult.deadLettered());
        assertEquals(expectedFirstExpireAt, enqueueResult.expiresAt());
        assertEquals(0, enqueueResult.numberOfRetries());

        Instant expectedSecondDeadLettered = setAndGetTime(expectedFirstExpireAt);
        Optional<DeadLetterEntry<M>> optionalResult = testSubject.enqueueIfPresent(testId, testSecondLetter);

        assertTrue(optionalResult.isPresent());
        DeadLetterEntry<M> enqueueIfPresentResult = optionalResult.get();
        assertEquals(testId, enqueueIfPresentResult.queueIdentifier());
        assertEquals(testSecondLetter, enqueueIfPresentResult.message());
        assertNull(enqueueIfPresentResult.cause());
        assertEquals(expectedSecondDeadLettered, enqueueIfPresentResult.deadLettered());
        // The expiresAt equals the deadLettered time whenever the message is enqueue due to a contained earlier entry.
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

        assertFalse(testSubject.contains(testId));

        testSubject.enqueue(testId, generateMessage(), generateCause());

        assertTrue(testSubject.contains(testId));
    }

    @Test
    void testIsEmpty() {
        I testId = generateQueueId();

        assertTrue(testSubject.isEmpty());

        testSubject.enqueue(testId, generateMessage(), generateCause());

        assertFalse(testSubject.isEmpty());

        // The queue should be empty again after releasing the only letter present
        testSubject.take(testId.group()).ifPresent(DeadLetterEntry::acknowledge);

        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testIsFullReturnsTrueAfterMaximumAmountOfQueuesIsReached() {
        assertFalse(testSubject.isFull(generateQueueId()));
        assertTrue(testSubject.isEmpty());

        long maxQueues = testSubject.maxQueues();
        assertTrue(maxQueues > 0);

        for (int i = 0; i < maxQueues; i++) {
            testSubject.enqueue(generateQueueId(), generateMessage(), generateCause());
        }

        assertTrue(testSubject.isFull(generateQueueId()));
    }

    @Test
    void testIsFullReturnsTrueAfterMaximumQueueSizeIsReached() {
        I testId = generateQueueId();

        assertFalse(testSubject.isFull(testId));
        assertTrue(testSubject.isEmpty());

        long maxQueueSize = testSubject.maxQueueSize();
        assertTrue(maxQueueSize > 0);

        for (int i = 0; i < maxQueueSize; i++) {
            testSubject.enqueue(testId, generateMessage(), generateCause());
        }

        assertTrue(testSubject.isFull(testId));
    }

    @Test
    void testTake() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        Throwable testCause = generateCause();
        M testSecondLetter = generateMessage();

        testSubject.enqueue(testId, testFirstLetter, testCause);
        testSubject.enqueue(testId, testSecondLetter, testCause);

        Optional<DeadLetterEntry<M>> firstOptionalResult = testSubject.take(testId.group());
        assertTrue(firstOptionalResult.isPresent());
        DeadLetterEntry<M> firstResult = firstOptionalResult.get();
        assertEquals(testId, firstResult.queueIdentifier());
        assertEquals(testFirstLetter, firstResult.message());
        assertEquals(testCause, firstResult.cause());
        assertEquals(expectedDeadLettered, firstResult.deadLettered());
        assertEquals(expectedExpireAt, firstResult.expiresAt());
        assertEquals(0, firstResult.numberOfRetries());
        firstResult.acknowledge();

        Optional<DeadLetterEntry<M>> secondOptionalResult = testSubject.take(testId.group());
        assertTrue(secondOptionalResult.isPresent());
        DeadLetterEntry<M> secondResult = secondOptionalResult.get();
        assertEquals(testId, secondResult.queueIdentifier());
        assertEquals(testSecondLetter, secondResult.message());
        assertEquals(testCause, secondResult.cause());
        assertEquals(expectedDeadLettered, secondResult.deadLettered());
        assertEquals(expectedExpireAt, secondResult.expiresAt());
        assertEquals(0, secondResult.numberOfRetries());

        // Only one entry was acknowledged, so the second still remains.
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testTakeOnEmptyQueueReturnsEmptyOptional() {
        String testGroup = "some-group";

        assertTrue(testSubject.isEmpty());
        assertFalse(testSubject.take(testGroup).isPresent());
    }

    @Test
    void testTakeForNonContainedGroupReturnsEmptyOptional() {
        I testId = generateQueueId();
        String testGroup = "some-group";
        assertNotEquals(testGroup, testId.group());

        assertTrue(testSubject.isEmpty());

        testSubject.enqueue(testId, generateMessage(), generateCause());
        testSubject.enqueue(testId, generateMessage(), generateCause());
        testSubject.enqueue(testId, generateMessage(), generateCause());

        assertFalse(testSubject.isEmpty());

        assertFalse(testSubject.take(testGroup).isPresent());
    }

    @Test
    void testTakeReturnsDeadLettersInInsertOrder() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testThisId = generateQueueId();
        M testThisFirstLetter = generateMessage();
        Throwable testThisCause = generateCause();
        M testThisSecondLetter = generateMessage();

        testSubject.enqueue(testThisId, testThisFirstLetter, testThisCause);
        testSubject.enqueueIfPresent(testThisId, testThisSecondLetter);

        I testThatId = generateQueueId();
        M testThatFirstLetter = generateMessage();
        Throwable testThatCause = generateCause();
        M testThatSecondLetter = generateMessage();

        testSubject.enqueue(testThatId, testThatFirstLetter, testThatCause);
        testSubject.enqueueIfPresent(testThatId, testThatSecondLetter);

        Optional<DeadLetterEntry<M>> thisOptionalFirstResult = testSubject.take(testThisId.group());
        assertTrue(thisOptionalFirstResult.isPresent());
        DeadLetterEntry<M> thisFirstResult = thisOptionalFirstResult.get();
        assertEquals(testThisId, thisFirstResult.queueIdentifier());
        assertEquals(testThisFirstLetter, thisFirstResult.message());
        assertEquals(testThisCause, thisFirstResult.cause());
        assertEquals(expectedDeadLettered, thisFirstResult.deadLettered());
        assertEquals(expectedExpireAt, thisFirstResult.expiresAt());
        assertEquals(0, thisFirstResult.numberOfRetries());
        thisFirstResult.acknowledge();

        Optional<DeadLetterEntry<M>> thisOptionalSecondResult = testSubject.take(testThisId.group());
        assertTrue(thisOptionalSecondResult.isPresent());
        DeadLetterEntry<M> thisSecondResult = thisOptionalSecondResult.get();
        assertEquals(testThisId, thisSecondResult.queueIdentifier());
        assertEquals(testThisSecondLetter, thisSecondResult.message());
        assertNull(thisSecondResult.cause());
        assertEquals(expectedDeadLettered, thisSecondResult.deadLettered());
        // The expiresAt equals the deadLettered time whenever the message is enqueue due to a contained earlier entry.
        assertEquals(expectedDeadLettered, thisSecondResult.expiresAt());
        assertEquals(0, thisSecondResult.numberOfRetries());
        thisSecondResult.acknowledge();

        Optional<DeadLetterEntry<M>> thatOptionalFirstResult = testSubject.take(testThatId.group());
        assertTrue(thatOptionalFirstResult.isPresent());
        DeadLetterEntry<M> thatFirstResult = thatOptionalFirstResult.get();
        assertEquals(testThatId, thatFirstResult.queueIdentifier());
        assertEquals(testThatFirstLetter, thatFirstResult.message());
        assertEquals(testThatCause, thatFirstResult.cause());
        assertEquals(expectedDeadLettered, thatFirstResult.deadLettered());
        assertEquals(expectedExpireAt, thatFirstResult.expiresAt());
        assertEquals(0, thatFirstResult.numberOfRetries());
        thatFirstResult.acknowledge();

        // The second 'that' letter is still in the queue.
        assertFalse(testSubject.isEmpty());
    }

    /**
     * An "active sequence" in this case means that a letter with {@link QueueIdentifier} {@code x} is not {@link
     * DeadLetterEntry#acknowledge() acknowledged} or {@link DeadLetterEntry#requeue() requeued} yet. Furthermore, if
     * it's the sole entry for that {@code group}, nothing should be returned. This approach ensure the events for a
     * given {@link QueueIdentifier} are handled in the order they've been dead-lettered (a.k.a., in sequence).
     */
    @Test
    void testTakeReturnsEmptyOptionalForAnActiveSequenceAndOtherwiseEmptyQueue() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        Throwable testThisCause = generateCause();
        M testSecondLetter = generateMessage();

        testSubject.enqueue(testId, testFirstLetter, testThisCause);
        testSubject.enqueueIfPresent(testId, testSecondLetter);

        Optional<DeadLetterEntry<M>> firstOptionalResult = testSubject.take(testId.group());
        assertTrue(firstOptionalResult.isPresent());
        DeadLetterEntry<M> firstResult = firstOptionalResult.get();
        assertEquals(testId, firstResult.queueIdentifier());
        assertEquals(testFirstLetter, firstResult.message());
        assertEquals(testThisCause, firstResult.cause());
        assertEquals(expectedDeadLettered, firstResult.deadLettered());
        assertEquals(expectedExpireAt, firstResult.expiresAt());
        assertEquals(0, firstResult.numberOfRetries());
        // No DeadLetterEntry#acknowledge or DeadLetterEntry#requeue invocation here, as that releases the sequence.

        assertFalse(testSubject.take(testId.group()).isPresent());
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testAcknowledgeRemovesLetterFromQueue() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testLetter = generateMessage();
        Throwable testCause = generateCause();

        testSubject.enqueue(testId, testLetter, testCause);

        assertFalse(testSubject.isEmpty());

        Optional<DeadLetterEntry<M>> optionalResult = testSubject.take(testId.group());
        assertTrue(optionalResult.isPresent());
        DeadLetterEntry<M> result = optionalResult.get();
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testLetter, result.message());
        assertEquals(testCause, result.cause());
        assertEquals(expectedDeadLettered, result.deadLettered());
        assertEquals(expectedExpireAt, result.expiresAt());
        assertEquals(0, result.numberOfRetries());

        // Evaluation of the dead-letter was successful, so its acknowledged and thus removed from the queue.
        result.acknowledge();

        assertTrue(testSubject.isEmpty());
        assertFalse(testSubject.take(testId.group()).isPresent());
    }

    @Test
    void testRequeueReenterLetterToQueueWithAdjustedExpireAtAndNumberOfRetries() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(expireThreshold());

        I testId = generateQueueId();
        M testLetter = generateMessage();
        Throwable testCause = generateCause();

        testSubject.enqueue(testId, testLetter, testCause);

        assertFalse(testSubject.isEmpty());

        Optional<DeadLetterEntry<M>> optionalFirstTryResult = testSubject.take(testId.group());
        assertTrue(optionalFirstTryResult.isPresent());
        DeadLetterEntry<M> firstTryResult = optionalFirstTryResult.get();
        assertEquals(testId, firstTryResult.queueIdentifier());
        assertEquals(testLetter, firstTryResult.message());
        assertEquals(testCause, firstTryResult.cause());
        assertEquals(expectedDeadLettered, firstTryResult.deadLettered());
        assertEquals(expectedExpireAt, firstTryResult.expiresAt());
        assertEquals(0, firstTryResult.numberOfRetries());

        Instant expectedUpdatedExpireAt = setAndGetTime().plus(expireThreshold());
        // Evaluation of the dead-letter was unsuccessful, so its requeued and thus kept in the queue.
        firstTryResult.requeue();

        assertFalse(testSubject.isEmpty());
        Optional<DeadLetterEntry<M>> optionalSecondTry = testSubject.take(testId.group());
        assertTrue(optionalSecondTry.isPresent());
        DeadLetterEntry<M> secondTryResult = optionalSecondTry.get();
        assertEquals(firstTryResult.queueIdentifier(), secondTryResult.queueIdentifier());
        assertEquals(firstTryResult.message(), secondTryResult.message());
        assertEquals(firstTryResult.cause(), secondTryResult.cause());
        assertEquals(firstTryResult.deadLettered(), secondTryResult.deadLettered());
        assertEquals(expectedUpdatedExpireAt, secondTryResult.expiresAt());
        assertEquals(1, secondTryResult.numberOfRetries());
    }

    @Test
    void testReleaseUpdatesExpireAtToNow() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plus(Duration.ofMillis(5000));

        I testThisId = generateQueueId();
        M testThisLetter = generateMessage();
        Throwable testThisCause = generateCause();
        testSubject.enqueue(testThisId, testThisLetter, testThisCause);

        I testThatId = generateQueueId();
        M testThatLetter = generateMessage();
        Throwable testThatCause = generateCause();
        testSubject.enqueue(testThatId, testThatLetter, testThatCause);

        // Set the time to the expected expireAt.
        setAndGetTime(expectedExpireAt);
        // Release all letters.
        testSubject.release();

        Optional<DeadLetterEntry<M>> thisOptionalResult = testSubject.take(testThisId.group());
        assertTrue(thisOptionalResult.isPresent());
        DeadLetterEntry<M> thisResult = thisOptionalResult.get();
        assertEquals(testThisId, thisResult.queueIdentifier());
        assertEquals(testThisLetter, thisResult.message());
        assertEquals(testThisCause, thisResult.cause());
        assertEquals(expectedDeadLettered, thisResult.deadLettered());
        assertEquals(expectedExpireAt, thisResult.expiresAt());
        assertEquals(0, thisResult.numberOfRetries());

        Optional<DeadLetterEntry<M>> thatOptionalResult = testSubject.take(testThatId.group());
        assertTrue(thatOptionalResult.isPresent());
        DeadLetterEntry<M> thatResult = thatOptionalResult.get();
        assertEquals(testThatId, thatResult.queueIdentifier());
        assertEquals(testThatLetter, thatResult.message());
        assertEquals(testThatCause, thatResult.cause());
        assertEquals(expectedDeadLettered, thatResult.deadLettered());
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
        Throwable testThisCause = generateCause();
        testSubject.enqueue(testThisId, testThisLetter, testThisCause);

        I testThatId = generateQueueId();
        M testThatLetter = generateMessage();
        Throwable testThatCause = generateCause();
        testSubject.enqueue(testThatId, testThatLetter, testThatCause);

        // Set the time to the expected expireAt.
        setAndGetTime(expectedUpdatedExpireAt);
        // Release letters matching testThatId.
        testSubject.release(entry -> entry.queueIdentifier().equals(testThatId));

        Optional<DeadLetterEntry<M>> thisOptionalResult = testSubject.take(testThisId.group());
        assertTrue(thisOptionalResult.isPresent());
        DeadLetterEntry<M> thisResult = thisOptionalResult.get();
        assertEquals(testThisId, thisResult.queueIdentifier());
        assertEquals(testThisLetter, thisResult.message());
        assertEquals(testThisCause, thisResult.cause());
        assertEquals(expectedDeadLettered, thisResult.deadLettered());
        assertEquals(expectedOriginalExpireAt, thisResult.expiresAt());
        assertEquals(0, thisResult.numberOfRetries());

        Optional<DeadLetterEntry<M>> thatOptionalResult = testSubject.take(testThatId.group());
        assertTrue(thatOptionalResult.isPresent());
        DeadLetterEntry<M> thatResult = thatOptionalResult.get();
        assertEquals(testThatId, thatResult.queueIdentifier());
        assertEquals(testThatLetter, thatResult.message());
        assertEquals(testThatCause, thatResult.cause());
        assertEquals(expectedDeadLettered, thatResult.deadLettered());
        assertEquals(expectedUpdatedExpireAt, thatResult.expiresAt());
        assertEquals(0, thatResult.numberOfRetries());
    }

    @Test
    void testReleaseInvokesAvailabilityCallbacksForUpdatedLetters() {
        AtomicBoolean thisExpectedCallbackInvoked = new AtomicBoolean(false);

        I testThisId = generateQueueId();
        M testThisLetter = generateMessage();
        Throwable testThisCause = generateCause();
        testSubject.enqueue(testThisId, testThisLetter, testThisCause);
        testSubject.onAvailable(testThisId.group(), () -> thisExpectedCallbackInvoked.set(true));

        AtomicBoolean thatExpectedCallbackInvoked = new AtomicBoolean(false);

        I testThatId = generateQueueId();
        M testThatLetter = generateMessage();
        Throwable testThatCause = generateCause();
        testSubject.enqueue(testThatId, testThatLetter, testThatCause);
        testSubject.onAvailable(testThatId.group(), () -> thatExpectedCallbackInvoked.set(true));

        // Release letters matching testThisId.
        testSubject.release(entry -> entry.queueIdentifier().equals(testThisId));

        assertWithin(100, TimeUnit.MILLISECONDS, () -> assertTrue(thisExpectedCallbackInvoked.get()));
        assertFalse(thatExpectedCallbackInvoked.get());
    }

    @Test
    void testClearRemovesAllEntries() {
        assertTrue(testSubject.isEmpty());

        testSubject.enqueue(generateQueueId(), generateMessage(), generateCause());
        testSubject.enqueue(generateQueueId(), generateMessage(), generateCause());
        testSubject.enqueue(generateQueueId(), generateMessage(), generateCause());

        assertFalse(testSubject.isEmpty());

        testSubject.clear();

        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testClearForGroupRemovesAllEntriesOfThatGroup() {
        I thisId = generateQueueId();
        I thatId = generateQueueId();

        assertTrue(testSubject.isEmpty());

        testSubject.enqueue(thisId, generateMessage(), generateCause());
        testSubject.enqueue(thisId, generateMessage(), generateCause());
        testSubject.enqueue(thatId, generateMessage(), generateCause());
        testSubject.enqueue(thatId, generateMessage(), generateCause());

        assertFalse(testSubject.isEmpty());

        testSubject.clear(thisId.group());

        assertFalse(testSubject.isEmpty());
        assertFalse(testSubject.contains(thisId));
        assertTrue(testSubject.contains(thatId));
    }

    @Test
    void testClearWithPredicateRemovesAllMatchingEntries() {
        I thisId = generateQueueId();
        I thatId = generateQueueId();
        I thirdId = generateQueueId();

        assertTrue(testSubject.isEmpty());

        testSubject.enqueue(thisId, generateMessage(), generateCause());
        testSubject.enqueue(thisId, generateMessage(), generateCause());
        testSubject.enqueue(thatId, generateMessage(), generateCause());
        testSubject.enqueue(thatId, generateMessage(), generateCause());
        testSubject.enqueue(thirdId, generateMessage(), generateCause());
        testSubject.enqueue(thirdId, generateMessage(), generateCause());

        assertFalse(testSubject.isEmpty());

        testSubject.clear(id -> id.equals(thisId) || id.equals(thirdId));

        assertFalse(testSubject.isEmpty());
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
     * @return A unique {@link Throwable} by using {@link #generateId()} in the cause description..
     */
    protected static Throwable generateCause() {
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
}
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

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
    abstract long expireThreshold();

    @Test
    void testEnqueue() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plusMillis(expireThreshold());

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
    void testEnqueueIfPresentDoesNotEnqueueForEmptyQueue() {
        I testQueueId = generateQueueId();

        Optional<DeadLetterEntry<M>> result = testSubject.enqueueIfPresent(testQueueId, generateMessage());

        assertFalse(result.isPresent());
        assertFalse(testSubject.contains(testQueueId));
        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForNonExistentIdGroupCombination() {
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
    void testEnqueueIfPresentEnqueuesForExistingIdGroupCombination() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plusMillis(expireThreshold());

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

        Optional<DeadLetterEntry<M>> peekedResult = testSubject.peek(testId.group());
        assertTrue(peekedResult.isPresent());
        assertEquals(enqueueResult, peekedResult.get());
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
        testSubject.peek(testId.group()).ifPresent(DeadLetterEntry::release);

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
    void testPeek() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plusMillis(expireThreshold());

        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        Throwable testCause = generateCause();
        M testSecondLetter = generateMessage();

        testSubject.enqueue(testId, testFirstLetter, testCause);
        testSubject.enqueue(testId, testSecondLetter, testCause);

        Optional<DeadLetterEntry<M>> firstOptionalResult = testSubject.peek(testId.group());
        assertTrue(firstOptionalResult.isPresent());
        DeadLetterEntry<M> firstResult = firstOptionalResult.get();
        assertEquals(testId, firstResult.queueIdentifier());
        assertEquals(testFirstLetter, firstResult.message());
        assertEquals(testCause, firstResult.cause());
        assertEquals(expectedDeadLettered, firstResult.deadLettered());
        assertEquals(expectedExpireAt, firstResult.expiresAt());
        firstResult.release();

        Optional<DeadLetterEntry<M>> secondOptionalResult = testSubject.peek(testId.group());
        assertTrue(secondOptionalResult.isPresent());
        DeadLetterEntry<M> secondResult = secondOptionalResult.get();
        assertEquals(testId, secondResult.queueIdentifier());
        assertEquals(testSecondLetter, secondResult.message());
        assertEquals(testCause, secondResult.cause());
        assertEquals(expectedDeadLettered, secondResult.deadLettered());
        assertEquals(expectedExpireAt, secondResult.expiresAt());

        // only one entry was released, so the second still remains
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testPeekOnEmptyQueueReturnsEmptyOptional() {
        String testGroup = "some-group";

        assertTrue(testSubject.isEmpty());
        assertFalse(testSubject.peek(testGroup).isPresent());
    }

    @Test
    void testPeekForNonContainedGroupReturnsEmptyOptional() {
        I testId = generateQueueId();
        String testGroup = "some-group";
        assertNotEquals(testGroup, testId.group());

        assertTrue(testSubject.isEmpty());

        testSubject.enqueue(testId, generateMessage(), generateCause());
        testSubject.enqueue(testId, generateMessage(), generateCause());
        testSubject.enqueue(testId, generateMessage(), generateCause());

        assertFalse(testSubject.isEmpty());

        assertFalse(testSubject.peek(testGroup).isPresent());
    }

    @Test
    void testPeekReturnsDeadLettersInInsertOrder() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plusMillis(expireThreshold());

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

        Optional<DeadLetterEntry<M>> thisOptionalFirstResult = testSubject.peek(testThisId.group());
        assertTrue(thisOptionalFirstResult.isPresent());
        DeadLetterEntry<M> thisFirstResult = thisOptionalFirstResult.get();
        assertEquals(testThisId, thisFirstResult.queueIdentifier());
        assertEquals(testThisFirstLetter, thisFirstResult.message());
        assertEquals(testThisCause, thisFirstResult.cause());
        assertEquals(expectedDeadLettered, thisFirstResult.deadLettered());
        assertEquals(expectedExpireAt, thisFirstResult.expiresAt());
        thisFirstResult.release();

        Optional<DeadLetterEntry<M>> thisOptionalSecondResult = testSubject.peek(testThisId.group());
        assertTrue(thisOptionalSecondResult.isPresent());
        DeadLetterEntry<M> thisSecondResult = thisOptionalSecondResult.get();
        assertEquals(testThisId, thisSecondResult.queueIdentifier());
        assertEquals(testThisSecondLetter, thisSecondResult.message());
        assertNull(thisSecondResult.cause());
        assertEquals(expectedDeadLettered, thisSecondResult.deadLettered());
        assertEquals(expectedExpireAt, thisSecondResult.expiresAt());
        thisSecondResult.release();

        Optional<DeadLetterEntry<M>> thatOptionalFirstResult = testSubject.peek(testThatId.group());
        assertTrue(thatOptionalFirstResult.isPresent());
        DeadLetterEntry<M> thatFirstResult = thatOptionalFirstResult.get();
        assertEquals(testThatId, thatFirstResult.queueIdentifier());
        assertEquals(testThatFirstLetter, thatFirstResult.message());
        assertEquals(testThatCause, thatFirstResult.cause());
        assertEquals(expectedDeadLettered, thatFirstResult.deadLettered());
        assertEquals(expectedExpireAt, thatFirstResult.expiresAt());
        thatFirstResult.release();

        // The second 'that' letter is still in the queue
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testReleaseRemovesLetterFromQueue() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plusMillis(expireThreshold());

        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        Throwable testCause = generateCause();

        testSubject.enqueue(testId, testFirstLetter, testCause);

        assertFalse(testSubject.isEmpty());

        Optional<DeadLetterEntry<M>> optionalResult = testSubject.peek(testId.group());
        assertTrue(optionalResult.isPresent());
        DeadLetterEntry<M> result = optionalResult.get();
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testFirstLetter, result.message());
        assertEquals(testCause, result.cause());
        assertEquals(expectedDeadLettered, result.deadLettered());
        assertEquals(expectedExpireAt, result.expiresAt());

        result.release();

        assertTrue(testSubject.isEmpty());
        assertFalse(testSubject.peek(testId.group()).isPresent());
    }

    @Test
    void testPeekWithoutReleaseUpdatesLetterInQueue() {
        Instant expectedDeadLettered = setAndGetTime();
        Instant expectedExpireAt = expectedDeadLettered.plusMillis(expireThreshold());

        I testId = generateQueueId();
        M testLetter = generateMessage();
        Throwable testCause = generateCause();

        testSubject.enqueue(testId, testLetter, testCause);
        // Update the clock to a time in the future in preparation for DeadLetterQueue#peek(String)
        // And, increment the result with the threshold for validation ASAP.
        Instant expectedUpdatedExpireAt = setAndGetTime(Instant.now().plusMillis(1337)).plusMillis(expireThreshold());

        assertFalse(testSubject.isEmpty());

        Optional<DeadLetterEntry<M>> optionalResult = testSubject.peek(testId.group());
        assertTrue(optionalResult.isPresent());
        DeadLetterEntry<M> result = optionalResult.get();
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testLetter, result.message());
        assertEquals(testCause, result.cause());
        assertEquals(expectedDeadLettered, result.deadLettered());
        assertEquals(expectedExpireAt, result.expiresAt());

        assertFalse(testSubject.isEmpty());

        optionalResult = testSubject.peek(testId.group());
        assertTrue(optionalResult.isPresent());
        result = optionalResult.get();
        assertEquals(testId, result.queueIdentifier());
        assertEquals(testLetter, result.message());
        assertEquals(testCause, result.cause());
        assertEquals(expectedDeadLettered, result.deadLettered());
        assertEquals(expectedUpdatedExpireAt, result.expiresAt());
    }

    @Test
    void testPeekWithoutReleaseAddsLetterToTheEndOfTheQueue() {
        Instant expectedFirstDeadLettered = setAndGetTime();
        Instant expectedFirstExpireAt = expectedFirstDeadLettered.plusMillis(expireThreshold());

        I testId = generateQueueId();
        M testFirstLetter = generateMessage();
        Throwable testFirstCause = generateCause();

        testSubject.enqueue(testId, testFirstLetter, testFirstCause);

        Instant expectedSecondDeadLettered = setAndGetTime(Instant.now().plusMillis(1337));
        Instant expectedSecondExpireAt = expectedSecondDeadLettered.plusMillis(expireThreshold());

        M testSecondLetter = generateMessage();
        Throwable testSecondCause = generateCause();

        testSubject.enqueue(testId, testSecondLetter, testSecondCause);
        // Update the clock to a time in the future in preparation for DeadLetterQueue#peek(String)
        // And, increment the result with the threshold for validation ASAP.
        Instant expectedUpdatedFirstExpireAt =
                setAndGetTime(Instant.now().plusMillis(1337)).plusMillis(expireThreshold());

        Optional<DeadLetterEntry<M>> firstOptionalResult = testSubject.peek(testId.group());
        // 'firstResult' is automatically reentered in the queue on peek
        assertTrue(firstOptionalResult.isPresent());
        DeadLetterEntry<M> firstResult = firstOptionalResult.get();
        assertEquals(testId, firstResult.queueIdentifier());
        assertEquals(testFirstLetter, firstResult.message());
        assertEquals(testFirstCause, firstResult.cause());
        assertEquals(expectedFirstDeadLettered, firstResult.deadLettered());
        assertEquals(expectedFirstExpireAt, firstResult.expiresAt());

        Optional<DeadLetterEntry<M>> secondOptionalResult = testSubject.peek(testId.group());
        // Peek generates 'secondResult'
        assertTrue(secondOptionalResult.isPresent());
        DeadLetterEntry<M> secondResult = secondOptionalResult.get();
        assertEquals(firstResult.queueIdentifier(), secondResult.queueIdentifier());
        assertEquals(testSecondLetter, secondResult.message());
        assertNotEquals(firstResult.message(), secondResult.message());
        assertEquals(testSecondCause, secondResult.cause());
        assertNotEquals(firstResult.cause(), secondResult.cause());
        assertEquals(expectedSecondDeadLettered, secondResult.deadLettered());
        assertNotEquals(firstResult.deadLettered(), secondResult.deadLettered());
        assertEquals(expectedSecondExpireAt, secondResult.expiresAt());
        assertNotEquals(firstResult.expiresAt(), secondResult.expiresAt());

        secondResult.release();
        // After successfully releasing the 'secondResult', the following entry is 'firstResult'

        Optional<DeadLetterEntry<M>> updateFirstOptionalResult = testSubject.peek(testId.group());
        assertTrue(updateFirstOptionalResult.isPresent());
        DeadLetterEntry<M> updatedFirstResult = updateFirstOptionalResult.get();
        assertEquals(firstResult.queueIdentifier(), updatedFirstResult.queueIdentifier());
        assertEquals(firstResult.message(), updatedFirstResult.message());
        assertEquals(firstResult.cause(), updatedFirstResult.cause());
        assertEquals(firstResult.deadLettered(), updatedFirstResult.deadLettered());
        assertNotEquals(firstResult.expiresAt(), updatedFirstResult.expiresAt());
        assertEquals(expectedUpdatedFirstExpireAt, updatedFirstResult.expiresAt());
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
     * @return a unique {@link String}, based on {@link UUID#randomUUID()}.
     */
    protected static String generateId() {
        return UUID.randomUUID().toString();
    }

    private static Throwable generateCause() {
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
/*
 * Copyright (c) 2010-2021. Axon Framework
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract class providing a generic test suite every {@link DeadLetterQueue} implementation should comply with.
 *
 * @author Steven van Beelen
 */
public abstract class DeadLetterQueueTest<M extends Message<?>> {

    private DeadLetterQueue<M> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = buildTestSubject();
    }

    /**
     * Constructs the {@link DeadLetterQueue} implementation under test.
     *
     * @return a {@link DeadLetterQueue} implementation under test
     */
    abstract DeadLetterQueue<M> buildTestSubject();

    /**
     * Constructs a {@link Message} implementation expected by the test subject.
     *
     * @return a {@link Message} implementation expected by the test subject
     */
    abstract M generateMessage();

    @Test
    void testEnqueue() {
        String testId = generateId();
        String testGroup = generateId();
        M testDeadLetter = generateMessage();
        Throwable testCause = generateCause();

        testSubject.enqueue(testId, testGroup, testDeadLetter, testCause);

        assertTrue(testSubject.contains(testId, testGroup));
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testEnqueueThrowsDeadLetterQueueFilledExceptionForFullQueue() {
        long maxSize = testSubject.maxSize();
        assertTrue(maxSize > 0);

        for (int i = 0; i < maxSize; i++) {
            testSubject.enqueue(generateId(), generateId(), generateMessage(), generateCause());
        }

        assertThrows(
                DeadLetterQueueFilledException.class,
                () -> testSubject.enqueue(generateId(), generateId(), generateMessage(), generateCause())
        );
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForEmptyQueue() {
        String testId = generateId();
        String testGroup = generateId();

        boolean result = testSubject.enqueueIfPresent(testId, testGroup, generateMessage());

        assertFalse(result);
        assertFalse(testSubject.contains(testId, testGroup));
        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testEnqueueIfPresentDoesNotEnqueueForNonExistentIdGroupCombination() {
        String testGroup = generateId();
        Throwable testCause = generateCause();
        String testFirstId = generateId();

        testSubject.enqueue(testFirstId, testGroup, generateMessage(), testCause);

        String testSecondId = generateId();

        boolean result = testSubject.enqueueIfPresent(testSecondId, testGroup, generateMessage());

        assertFalse(result);
        assertTrue(testSubject.contains(testFirstId, testGroup));
        assertFalse(testSubject.contains(testSecondId, testGroup));
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testEnqueueIfPresentEnqueuesForExistingIdGroupCombination() {
        String testId = generateId();
        String testGroup = generateId();
        Throwable testCause = generateCause();

        // First dead letter inserted...
        M testFirstLetter = generateMessage();
        testSubject.enqueue(testId, testGroup, testFirstLetter, testCause);
        // Second dead letter inserted...
        M testSecondLetter = generateMessage();
        boolean result = testSubject.enqueueIfPresent(testId, testGroup, testSecondLetter);

        assertTrue(result);
        assertTrue(testSubject.contains(testId, testGroup));
        assertFalse(testSubject.isEmpty());

        DeadLetterEntry<M> resultEntry = testSubject.peek();
        assertEquals(testId, resultEntry.identifier());
        assertEquals(testGroup, resultEntry.group());
        assertEquals(testFirstLetter, resultEntry.message());
        assertEquals(testCause, resultEntry.cause());
    }

    @Test
    void testContains() {
        String testId = generateId();
        String testGroup = generateId();

        assertFalse(testSubject.contains(testId, testGroup));

        testSubject.enqueue(testId, testGroup, generateMessage(), generateCause());

        assertTrue(testSubject.contains(testId, testGroup));
    }

    @Test
    void testIsEmpty() {
        assertTrue(testSubject.isEmpty());

        testSubject.enqueue(generateId(), generateId(), generateMessage(), generateCause());

        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testIsFull() {
        assertFalse(testSubject.isFull());

        long maxSize = testSubject.maxSize();
        assertTrue(maxSize > 0);

        for (int i = 0; i < maxSize; i++) {
            testSubject.enqueue(generateId(), generateId(), generateMessage(), generateCause());
        }

        assertTrue(testSubject.isFull());
    }

    @Test
    void testPeek() {
        String testId = generateId();
        String testGroup = generateId();
        M testFirstLetter = generateMessage();
        Throwable testCause = generateCause();
        M testSecondLetter = generateMessage();

        testSubject.enqueue(testId, testGroup, testFirstLetter, testCause);
        testSubject.enqueue(testId, testGroup, testSecondLetter, testCause);

        DeadLetterEntry<M> firstResult = testSubject.peek();
        assertEquals(testId, firstResult.identifier());
        assertEquals(testGroup, firstResult.group());
        assertEquals(testFirstLetter, firstResult.message());
        assertEquals(testCause, firstResult.cause());
        testSubject.evaluationSucceeded(firstResult);

        DeadLetterEntry<M> secondResult = testSubject.peek();
        assertEquals(testId, secondResult.identifier());
        assertEquals(testGroup, secondResult.group());
        assertEquals(testSecondLetter, secondResult.message());
        assertEquals(testCause, secondResult.cause());

        // only one entry was evaluated successfully, so the second still remains
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testPeekOnEmptyQueueReturnsNull() {
        assertNull(testSubject.peek());
    }

    @Test
    void testPeekReturnsDeadLettersInInsertOrder() {
        String testThisId = generateId();
        String testThisGroup = generateId();
        M testThisFirstLetter = generateMessage();
        Throwable testThisCause = generateCause();
        M testThisSecondLetter = generateMessage();

        testSubject.enqueue(testThisId, testThisGroup, testThisFirstLetter, testThisCause);
        testSubject.enqueueIfPresent(testThisId, testThisGroup, testThisSecondLetter);

        String testThatId = generateId();
        String testThatGroup = generateId();
        M testThatFirstLetter = generateMessage();
        Throwable testThatCause = generateCause();
        M testThatSecondLetter = generateMessage();

        testSubject.enqueue(testThatId, testThatGroup, testThatFirstLetter, testThatCause);
        testSubject.enqueueIfPresent(testThatId, testThatGroup, testThatSecondLetter);

        DeadLetterEntry<M> thisFirstResult = testSubject.peek();
        assertEquals(testThisId, thisFirstResult.identifier());
        assertEquals(testThisGroup, thisFirstResult.group());
        assertEquals(testThisFirstLetter, thisFirstResult.message());
        assertEquals(testThisCause, thisFirstResult.cause());
        testSubject.evaluationSucceeded(thisFirstResult);

        DeadLetterEntry<M> thisSecondResult = testSubject.peek();
        assertEquals(testThisId, thisSecondResult.identifier());
        assertEquals(testThisGroup, thisSecondResult.group());
        assertEquals(testThisSecondLetter, thisSecondResult.message());
        assertNull(thisSecondResult.cause());
        testSubject.evaluationSucceeded(thisSecondResult);

        DeadLetterEntry<M> thatFirstResult = testSubject.peek();
        assertEquals(testThatId, thatFirstResult.identifier());
        assertEquals(testThatGroup, thatFirstResult.group());
        assertEquals(testThatFirstLetter, thatFirstResult.message());
        assertEquals(testThatCause, thatFirstResult.cause());
        testSubject.evaluationSucceeded(thatFirstResult);

        // The second 'that' letter is still in the queue
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testEvaluationSucceededRemovesLetterFromQueue() {
        String testId = generateId();
        String testGroup = generateId();
        M testFirstLetter = generateMessage();
        Throwable testCause = generateCause();

        testSubject.enqueue(testId, testGroup, testFirstLetter, testCause);

        assertFalse(testSubject.isEmpty());

        DeadLetterEntry<M> result = testSubject.peek();
        assertEquals(testId, result.identifier());
        assertEquals(testGroup, result.group());
        assertEquals(testFirstLetter, result.message());
        assertEquals(testCause, result.cause());

        testSubject.evaluationSucceeded(result);

        assertTrue(testSubject.isEmpty());
        assertNull(testSubject.peek());
    }

    @Test
    void testEvaluationFailedUpdatesLetterInQueue() {
        String testId = generateId();
        String testGroup = generateId();
        M testFirstLetter = generateMessage();
        Throwable testFirstCause = generateCause();
        Throwable testSecondCause = generateCause();

        testSubject.enqueue(testId, testGroup, testFirstLetter, testFirstCause);

        assertFalse(testSubject.isEmpty());

        DeadLetterEntry<M> result = testSubject.peek();
        assertEquals(testId, result.identifier());
        assertEquals(testGroup, result.group());
        assertEquals(testFirstLetter, result.message());
        assertEquals(testFirstCause, result.cause());

        testSubject.evaluationFailed(result, testSecondCause);

        assertFalse(testSubject.isEmpty());
        result = testSubject.peek();
        assertEquals(testId, result.identifier());
        assertEquals(testGroup, result.group());
        assertEquals(testFirstLetter, result.message());
        assertEquals(testSecondCause, result.cause());
    }

    @Test
    void testEvaluationFailedAddsLetterToTheEndOfTheQueue() {
        String testThisId = generateId();
        String testThisGroup = generateId();
        M testThisLetter = generateMessage();
        Throwable testThisFirstCause = generateCause();
        Throwable testThisSecondCause = generateCause();

        testSubject.enqueue(testThisId, testThisGroup, testThisLetter, testThisFirstCause);

        String testThatId = generateId();
        String testThatGroup = generateId();
        M testThatLetter = generateMessage();
        Throwable testThatCause = generateCause();

        testSubject.enqueue(testThatId, testThatGroup, testThatLetter, testThatCause);

        DeadLetterEntry<M> thisResult = testSubject.peek();
        assertEquals(testThisId, thisResult.identifier());
        assertEquals(testThisGroup, thisResult.group());
        assertEquals(testThisLetter, thisResult.message());
        assertEquals(testThisFirstCause, thisResult.cause());
        // 'thisResult' is reentered in the queue at the end
        testSubject.evaluationFailed(thisResult, testThisSecondCause);

        // Peek generates 'thatResult'
        DeadLetterEntry<M> thatResult = testSubject.peek();
        assertNotEquals(thisResult.identifier(), thatResult.identifier());
        assertNotEquals(thisResult.group(), thatResult.group());
        assertNotEquals(thisResult.message(), thatResult.message());
        assertNotEquals(thisResult.cause(), thatResult.cause());
        testSubject.evaluationSucceeded(thatResult);

        // After successfully evaluating 'thatResult', the following entry is 'thisResult' with a new cause
        DeadLetterEntry<M> thisUpdatedResult = testSubject.peek();
        assertEquals(thisResult.identifier(), thisUpdatedResult.identifier());
        assertEquals(thisResult.group(), thisUpdatedResult.group());
        assertEquals(thisResult.message(), thisUpdatedResult.message());
        assertNotEquals(thisResult.cause(), thisUpdatedResult.cause());
        assertEquals(testThisSecondCause, thisUpdatedResult.cause());
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }

    private static Throwable generateCause() {
        return new RuntimeException("Because..." + generateId());
    }
}
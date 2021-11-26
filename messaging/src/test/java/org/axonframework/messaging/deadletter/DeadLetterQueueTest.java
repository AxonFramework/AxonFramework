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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.deadletter.GenericEventDeadLetter;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract class providing a generic test set every {@link DeadLetterQueue} implementation should comply with.
 *
 * @author Steven van Beelen
 */
public abstract class DeadLetterQueueTest {

    private DeadLetterQueue<EventMessage<?>> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = buildTestSubject();
    }

    /**
     * Constructs the {@link DeadLetterQueue} implementation under test.
     *
     * @return a {@link DeadLetterQueue} implementation under test
     */
    abstract DeadLetterQueue<EventMessage<?>> buildTestSubject();

    @Test
    void testAdd() {
        String testSequenceId = generateSequenceId();
        DeadLetter<EventMessage<?>> testDeadLetter = generateDeadLetter(testSequenceId);

        testSubject.add(testDeadLetter);

        assertTrue(testSubject.contains(testSequenceId));
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testAddIfPresentDoesNotAddForEmptyQueue() {
        String testSequenceId = generateSequenceId();

        boolean result = testSubject.addIfPresent(generateDeadLetter(testSequenceId));

        assertFalse(result);
        assertFalse(testSubject.contains(testSequenceId));
        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testAddIfPresentDoesNotAddForNonExistentSequenceId() {
        String testFirstSequenceId = generateSequenceId();
        testSubject.add(generateDeadLetter(testFirstSequenceId));

        String testSecondSequenceId = generateSequenceId();

        boolean result = testSubject.addIfPresent(generateDeadLetter(testFirstSequenceId));

        assertTrue(result);
        assertTrue(testSubject.contains(testFirstSequenceId));
        assertFalse(testSubject.contains(testSecondSequenceId));
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testAddIfPresentAddsForExistingSequence() {
        String testSequenceId = generateSequenceId();
        DeadLetter<EventMessage<?>> testFirstDeadLetter = generateDeadLetter(testSequenceId);
        testSubject.add(testFirstDeadLetter);
        DeadLetter<EventMessage<?>> testSecondDeadLetter = generateDeadLetter(testSequenceId);

        boolean result = testSubject.addIfPresent(testSecondDeadLetter);

        assertTrue(result);
        assertTrue(testSubject.contains(testSequenceId));
        assertFalse(testSubject.isEmpty());
        List<DeadLetter<EventMessage<?>>> resultQueue = testSubject.peek().collect(Collectors.toList());
        assertTrue(resultQueue.contains(testFirstDeadLetter));
        assertTrue(resultQueue.contains(testSecondDeadLetter));
    }

    @Test
    void testIsPresent() {
        String testSequenceId = generateSequenceId();

        assertFalse(testSubject.contains(testSequenceId));

        testSubject.add(generateDeadLetter(testSequenceId));

        assertTrue(testSubject.contains(testSequenceId));
    }

    @Test
    void testIsEmpty() {
        assertTrue(testSubject.isEmpty());

        testSubject.add(generateDeadLetter());

        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testPeek() {
        String testSequenceId = generateSequenceId();
        DeadLetter<EventMessage<?>> testFirstDeadLetter = generateDeadLetter(testSequenceId);
        DeadLetter<EventMessage<?>> testSecondDeadLetter = generateDeadLetter(testSequenceId);

        testSubject.add(testFirstDeadLetter);
        testSubject.add(testSecondDeadLetter);

        List<DeadLetter<EventMessage<?>>> result = testSubject.peek().collect(Collectors.toList());
        assertTrue(result.contains(testFirstDeadLetter));
        assertTrue(result.contains(testSecondDeadLetter));

        // peek keeps entries in the dead letter queue
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testPeekOnEmptyQueueReturnsEmptyStream() {
        List<DeadLetter<EventMessage<?>>> result = testSubject.peek().collect(Collectors.toList());

        assertTrue(result.isEmpty());
    }

    @Test
    void testPeekReturnsDeadLetterStreamsInInsertOrder() {
        String testThisSequenceId = generateSequenceId();
        DeadLetter<EventMessage<?>> testThisFirstDeadLetter = generateDeadLetter(testThisSequenceId);
        DeadLetter<EventMessage<?>> testThisSecondDeadLetter = generateDeadLetter(testThisSequenceId);

        testSubject.add(testThisFirstDeadLetter);
        testSubject.add(testThisSecondDeadLetter);


        String testThatSequenceId = generateSequenceId();
        DeadLetter<EventMessage<?>> testThatFirstDeadLetter = generateDeadLetter(testThatSequenceId);
        DeadLetter<EventMessage<?>> testThatSecondDeadLetter = generateDeadLetter(testThatSequenceId);

        testSubject.add(testThatFirstDeadLetter);
        testSubject.add(testThatSecondDeadLetter);

        List<DeadLetter<EventMessage<?>>> thisResult = testSubject.peek().collect(Collectors.toList());
        assertTrue(thisResult.contains(testThisFirstDeadLetter));
        assertTrue(thisResult.contains(testThisSecondDeadLetter));

        // peeking again will result in the same entry
        assertArrayEquals(thisResult.toArray(), testSubject.peek().toArray());

        // peek keeps entries in the dead letter queue
        assertFalse(testSubject.isEmpty());
    }

    @Test
    void testPoll() {
        String testSequenceId = generateSequenceId();
        DeadLetter<EventMessage<?>> testFirstDeadLetter = generateDeadLetter(testSequenceId);
        DeadLetter<EventMessage<?>> testSecondDeadLetter = generateDeadLetter(testSequenceId);

        testSubject.add(testFirstDeadLetter);
        testSubject.add(testSecondDeadLetter);

        List<DeadLetter<EventMessage<?>>> result = testSubject.poll().collect(Collectors.toList());
        assertTrue(result.contains(testFirstDeadLetter));
        assertTrue(result.contains(testSecondDeadLetter));

        // poll removes entries in the dead letter queue
        assertTrue(testSubject.isEmpty());
    }

    @Test
    void testPollOnEmptyQueueReturnsEmptyStream() {
        List<DeadLetter<EventMessage<?>>> result = testSubject.poll().collect(Collectors.toList());

        assertTrue(result.isEmpty());
    }

    @Test
    void testPollReturnsDeadLetterStreamsInInsertOrder() {
        String testThisSequenceId = generateSequenceId();
        DeadLetter<EventMessage<?>> testThisFirstDeadLetter = generateDeadLetter(testThisSequenceId);
        DeadLetter<EventMessage<?>> testThisSecondDeadLetter = generateDeadLetter(testThisSequenceId);

        testSubject.add(testThisFirstDeadLetter);
        testSubject.add(testThisSecondDeadLetter);

        String testThatSequenceId = generateSequenceId();
        DeadLetter<EventMessage<?>> testThatFirstDeadLetter = generateDeadLetter(testThatSequenceId);
        DeadLetter<EventMessage<?>> testThatSecondDeadLetter = generateDeadLetter(testThatSequenceId);

        testSubject.add(testThatFirstDeadLetter);
        testSubject.add(testThatSecondDeadLetter);

        List<DeadLetter<EventMessage<?>>> thisResult = testSubject.poll().collect(Collectors.toList());
        assertTrue(thisResult.contains(testThisFirstDeadLetter));
        assertTrue(thisResult.contains(testThisSecondDeadLetter));

        assertFalse(testSubject.isEmpty());

        List<DeadLetter<EventMessage<?>>> thatResult = testSubject.poll().collect(Collectors.toList());
        assertTrue(thatResult.contains(testThisFirstDeadLetter));
        assertTrue(thatResult.contains(testThisSecondDeadLetter));

        assertTrue(testSubject.isEmpty());
    }

    private static String generateSequenceId() {
        return UUID.randomUUID().toString();
    }

    private static DeadLetter<EventMessage<?>> generateDeadLetter() {
        return generateDeadLetter(generateSequenceId());
    }

    private static DeadLetter<EventMessage<?>> generateDeadLetter(String sequenceIdentifier) {
        return new GenericEventDeadLetter(sequenceIdentifier, GenericEventMessage.asEventMessage(generateSequenceId()));
    }
}
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link InMemorySequencedDeadLetterQueue}.
 *
 * @author Steven van Beelen
 */
class InMemorySequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {

    @Override
    public SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
        return InMemorySequencedDeadLetterQueue.defaultQueue();
    }

    @Override
    public DeadLetter<EventMessage<?>> generateInitialLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable());
    }

    @Override
    protected DeadLetter<EventMessage<?>> generateFollowUpLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent());
    }

    @Override
    protected DeadLetter<EventMessage<?>> generateRequeuedLetter(DeadLetter<EventMessage<?>> original,
                                                                 Instant lastTouched,
                                                                 Throwable requeueCause,
                                                                 MetaData diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause)
                       .withDiagnostics(diagnostics)
                       .markTouched();
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
    }

    @Test
    void testMaxSequences() {
        int expectedMaxSequences = 128;

        InMemorySequencedDeadLetterQueue<EventMessage<?>> testSubject =
                InMemorySequencedDeadLetterQueue.<EventMessage<?>>builder()
                                                .maxSequences(expectedMaxSequences)
                                                .build();

        assertEquals(expectedMaxSequences, testSubject.maxSequences());
    }

    @Test
    void testMaxSequenceSize() {
        int expectedMaxSequenceSize = 128;

        InMemorySequencedDeadLetterQueue<EventMessage<?>> testSubject =
                InMemorySequencedDeadLetterQueue.<EventMessage<?>>builder()
                                                .maxSequenceSize(expectedMaxSequenceSize)
                                                .build();

        assertEquals(expectedMaxSequenceSize, testSubject.maxSequenceSize());
    }

    @Test
    void testBuildDefaultQueue() {
        assertDoesNotThrow(() -> InMemorySequencedDeadLetterQueue.defaultQueue());
    }

    @Test
    void testBuildWithNegativeMaxSequencesThrowsAxonConfigurationException() {
        InMemorySequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject =
                InMemorySequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(-1));
    }

    @Test
    void testBuildWithValueLowerThanMinimumMaxSequencesThrowsAxonConfigurationException() {
        IntStream.range(0, 127).forEach(i -> {
            InMemorySequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject =
                    InMemorySequencedDeadLetterQueue.builder();

            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(i));
        });
    }

    @Test
    void testBuildWithNegativeMaxSequenceSizeThrowsAxonConfigurationException() {
        InMemorySequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject =
                InMemorySequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(-1));
    }

    @Test
    void testBuildWithValueLowerThanMinimumMaxSequenceSizeThrowsAxonConfigurationException() {
        IntStream.range(0, 127).forEach(i -> {
            InMemorySequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject =
                    InMemorySequencedDeadLetterQueue.builder();

            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(i));
        });
    }
}

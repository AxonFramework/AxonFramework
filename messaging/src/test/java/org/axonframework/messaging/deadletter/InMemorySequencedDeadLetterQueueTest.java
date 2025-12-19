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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class validating the {@link InMemorySequencedDeadLetterQueue}.
 *
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class InMemorySequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 128;

    @Override
    protected SequencedDeadLetterQueue<EventMessage> buildTestSubject() {
        return InMemorySequencedDeadLetterQueue.<EventMessage>builder()
                                               .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                               .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                               .build();
    }

    @Override
    protected long maxSequences() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    protected long maxSequenceSize() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    public DeadLetter<EventMessage> generateInitialLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable());
    }

    @Override
    protected DeadLetter<EventMessage> generateFollowUpLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent());
    }

    @Override
    protected DeadLetter<EventMessage> generateRequeuedLetter(DeadLetter<EventMessage> original,
                                                              Instant lastTouched,
                                                              Throwable requeueCause,
                                                              Metadata diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause)
                       .withDiagnostics(diagnostics)
                       .markTouched();
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
    }

    @Nested
    class WhenBuilding {

        @Test
        void buildDefaultQueue() {
            // when / then
            assertDoesNotThrow(() -> InMemorySequencedDeadLetterQueue.defaultQueue());
        }

        @Test
        void buildWithNegativeMaxSequencesThrowsAxonConfigurationException() {
            // given
            InMemorySequencedDeadLetterQueue.Builder<EventMessage> builderTestSubject =
                    InMemorySequencedDeadLetterQueue.builder();

            // when / then
            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(-1));
        }

        @Test
        void buildWithZeroMaxSequencesThrowsAxonConfigurationException() {
            // given
            InMemorySequencedDeadLetterQueue.Builder<EventMessage> builderTestSubject =
                    InMemorySequencedDeadLetterQueue.builder();

            // when / then
            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(0));
        }

        @Test
        void buildWithNegativeMaxSequenceSizeThrowsAxonConfigurationException() {
            // given
            InMemorySequencedDeadLetterQueue.Builder<EventMessage> builderTestSubject =
                    InMemorySequencedDeadLetterQueue.builder();

            // when / then
            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(-1));
        }

        @Test
        void buildWithZeroMaxSequenceSizeThrowsAxonConfigurationException() {
            // given
            InMemorySequencedDeadLetterQueue.Builder<EventMessage> builderTestSubject =
                    InMemorySequencedDeadLetterQueue.builder();

            // when / then
            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(0));
        }
    }
}

/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.junit.jupiter.api.*;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DeadLetteringCacheTest {

    private final int CACHE_SIZE = 10;
    private DeadLetteringCacheEntry testSubject;
    private SequencedDeadLetterQueue<EventMessage<?>> mockSequencedDeadLetterQueue;

    @BeforeEach
    void setUp() {
        mockSequencedDeadLetterQueue = mock(SequencedDeadLetterQueue.class);
        doReturn(0L).when(mockSequencedDeadLetterQueue).amountOfSequences();
        testSubject = new DeadLetteringCacheEntry(2, CACHE_SIZE, mockSequencedDeadLetterQueue);
    }

    @Test
    void skipCallIfDlqEmpty() {
        String sequenceIdentifier = UUID.randomUUID().toString();
        assertTrue(testSubject.skipIfPresentCheck(sequenceIdentifier));
    }

    @Test
    void skipOnceIfDlqNotEmpty() {
        doReturn(10L).when(mockSequencedDeadLetterQueue).amountOfSequences();
        testSubject = new DeadLetteringCacheEntry(2, CACHE_SIZE, mockSequencedDeadLetterQueue);

        String sequenceIdentifier = UUID.randomUUID().toString();
        assertFalse(testSubject.skipIfPresentCheck(sequenceIdentifier));

        testSubject.markNotPresentInDLQ(sequenceIdentifier);
        assertTrue(testSubject.skipIfPresentCheck(sequenceIdentifier));
    }

    @Test
    void cacheSizeIsUsed() {
        doReturn(10L).when(mockSequencedDeadLetterQueue).amountOfSequences();
        testSubject = new DeadLetteringCacheEntry(2, CACHE_SIZE, mockSequencedDeadLetterQueue);

        String sequenceIdentifier = UUID.randomUUID().toString();
        testSubject.markNotPresentInDLQ(sequenceIdentifier);

        IntStream.range(0, CACHE_SIZE).forEach(i -> testSubject.markNotPresentInDLQ(UUID.randomUUID().toString()));
        assertFalse(testSubject.skipIfPresentCheck(sequenceIdentifier));
    }

    @Test
    void notSkippedIfPutInDlq() {
        String sequenceIdentifier = UUID.randomUUID().toString();
        testSubject.markPresentInDLQ(sequenceIdentifier);
        assertFalse(testSubject.skipIfPresentCheck(sequenceIdentifier));
    }

    @Test
    void skippedAgainAfterDlqEmptied() {
        String sequenceIdentifier = UUID.randomUUID().toString();
        testSubject.markPresentInDLQ(sequenceIdentifier);
        testSubject.markNotPresentInDLQ(sequenceIdentifier);
        assertTrue(testSubject.skipIfPresentCheck(sequenceIdentifier));
    }
}

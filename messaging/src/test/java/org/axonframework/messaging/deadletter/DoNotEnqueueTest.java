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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link DoNotEnqueue}. Constructs a {@code DoNotEnqueue} through the constructor and
 * {@link Decisions} utility class for testing.
 *
 * @author Steven van Beelen
 */
class DoNotEnqueueTest {

    private DeadLetter<EventMessage<String>> testLetter;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        testLetter = new GenericDeadLetter<>("seqId", EventTestUtils.asEventMessage("payload"));
    }

    @Test
    void constructorDoNotEnqueueDoesNotAllowEnqueueing() {
        DoNotEnqueue<Message<?>> testSubject = new DoNotEnqueue<>();

        assertFalse(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void decisionsDoNotEnqueueDoesNotAllowEnqueueing() {
        DoNotEnqueue<Message<?>> testSubject = Decisions.doNotEnqueue();

        assertFalse(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void decisionsEvictDoesNotAllowEnqueueing() {
        DoNotEnqueue<Message<?>> testSubject = Decisions.evict();

        assertFalse(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }
}
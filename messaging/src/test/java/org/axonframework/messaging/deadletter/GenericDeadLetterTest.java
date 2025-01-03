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
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericDeadLetter}.
 *
 * @author Steven van Beelen
 */
class GenericDeadLetterTest {

    private static final String SEQUENCE_IDENTIFIER = "sesequenceIdentifier";
    private static final EventMessage<String> MESSAGE = EventTestUtils.asEventMessage("payload");

    @Test
    void constructorForIdentifierAndMessageSetsGivenIdentifierAndMessage() {
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        assertEquals(MESSAGE, testSubject.message());
        assertFalse(testSubject.cause().isPresent());
        assertEquals(expectedTime, testSubject.enqueuedAt());
        assertEquals(expectedTime, testSubject.lastTouched());
        assertTrue(testSubject.diagnostics().isEmpty());
    }

    @Test
    void constructorForIdentifierMessageAndThrowableSetsGivenIdentifierMessageAndIdentifier() {
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE, testThrowable);

        assertEquals(MESSAGE, testSubject.message());
        Optional<Cause> resultCause = testSubject.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(expectedTime, testSubject.enqueuedAt());
        assertEquals(expectedTime, testSubject.lastTouched());
        assertTrue(testSubject.diagnostics().isEmpty());
    }

    @Test
    void constructorCompletelyManualSetsGivenFields() {
        ThrowableCause expectedCause = new ThrowableCause(new RuntimeException("just because"));
        Instant expectedEnqueuedAt = Instant.now();
        Instant expectedLastTouched = Instant.now();
        MetaData expectedDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(
                SEQUENCE_IDENTIFIER, MESSAGE, expectedCause, expectedEnqueuedAt, expectedLastTouched,
                expectedDiagnostics
        );

        assertEquals(MESSAGE, testSubject.message());
        Optional<Cause> resultCause = testSubject.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(expectedEnqueuedAt, testSubject.enqueuedAt());
        assertEquals(expectedLastTouched, testSubject.lastTouched());
        assertEquals(expectedDiagnostics, testSubject.diagnostics());
    }

    @Test
    void invokingMarkTouchedAdjustsLastTouched() {
        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        Instant expectedLastTouched = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedLastTouched, ZoneId.systemDefault());
        DeadLetter<EventMessage<String>> result = testSubject.markTouched();

        assertEquals(testSubject.message(), result.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(expectedLastTouched, result.lastTouched());
        assertEquals(testSubject.diagnostics(), result.diagnostics());
    }

    @Test
    void invokingWithCauseAndWithoutOriginalCauseSetsGivenCause() {
        // Fix the clock to keep time consistent after withCause invocation.
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(testThrowable);

        assertEquals(testSubject.message(), result.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(testSubject.diagnostics(), result.diagnostics());
    }

    @Test
    void invokingWithCauseAndOriginalCauseReplacesTheOriginalCause() {
        // Fix the clock to keep time consistent after withCause invocation.
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        Throwable originalThrowable = new RuntimeException("some other issue");
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE, originalThrowable);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(testThrowable);

        assertEquals(testSubject.message(), result.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(testSubject.diagnostics(), result.diagnostics());
    }

    @Test
    void invokingWithNullCauseAndNoOriginalCauseLeavesTheCauseEmpty() {
        // Fix the clock to keep time consistent after withCause invocation.
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertEquals(testSubject.message(), result.message());
        assertFalse(result.cause().isPresent());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(testSubject.diagnostics(), result.diagnostics());
    }

    @Test
    void invokingWithNullCauseAndOriginalCauseKeepsTheOriginalCause() {
        // Fix the clock to keep time consistent after withCause invocation.
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE, testThrowable);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertEquals(testSubject.message(), result.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(resultCause.get(), expectedCause);
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(testSubject.diagnostics(), result.diagnostics());
    }

    @Test
    void invokingWithDiagnosticsReplacesTheOriginalDiagnostics() {
        // Fix the clock to keep time consistent after withDiagnostics invocation.
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        MetaData originalDiagnostics = MetaData.with("old-key", "old-value");
        MetaData expectedDiagnostics = MetaData.with("new-key", "new-value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(
                SEQUENCE_IDENTIFIER, MESSAGE, null, expectedTime, expectedTime, originalDiagnostics
        );

        DeadLetter<EventMessage<String>> result = testSubject.withDiagnostics(expectedDiagnostics);

        assertEquals(testSubject.message(), result.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(expectedDiagnostics, result.diagnostics());
    }

    @Test
    void invokingWithDiagnosticsBuilderAppendsTheOriginalDiagnostics() {
        // Fix the clock to keep time consistent after withDiagnostics invocation.
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        MetaData originalDiagnostics = MetaData.with("old-key", "old-value");
        MetaData expectedDiagnostics = MetaData.with("old-key", "old-value").and("new-key", "new-value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(
                SEQUENCE_IDENTIFIER, MESSAGE, null, expectedTime, expectedTime, originalDiagnostics
        );

        DeadLetter<EventMessage<String>> result =
                testSubject.withDiagnostics(original -> original.and("new-key", "new-value"));

        assertEquals(testSubject.message(), result.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(expectedDiagnostics, result.diagnostics());
    }
}
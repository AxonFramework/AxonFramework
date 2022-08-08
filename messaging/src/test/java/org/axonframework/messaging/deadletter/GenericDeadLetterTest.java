package org.axonframework.messaging.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
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

    private static final String SEQUENCE_IDENTIFIER = "seqId";
    private static final EventMessage<String> MESSAGE = GenericEventMessage.asEventMessage("payload");

    @Test
    void testConstructForIdentifierAndMessage() {
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
    void testConstructForIdentifierMessageAndThrowable() {
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
    void testConstructCompletelyManual() {
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
    void testMarkTouched() {
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
    void testWithCauseAndNoOriginalCause() {
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
    void testWithCauseAndOriginalCause() {
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
    void testWithNullCauseAndNoOriginalCause() {
        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertEquals(testSubject.message(), result.message());
        assertFalse(result.cause().isPresent());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(testSubject.diagnostics(), result.diagnostics());
    }

    @Test
    void testWithNullCauseAndOriginalCause() {
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
    void testWithDiagnostics() {
        MetaData expectedDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withDiagnostics(expectedDiagnostics);

        assertEquals(testSubject.message(), result.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(expectedDiagnostics, result.diagnostics());
    }

    @Test
    void testWithDiagnosticsBuilder() {
        MetaData expectedDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withDiagnostics(original -> original.and("key", "value"));

        assertEquals(testSubject.message(), result.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(testSubject.enqueuedAt(), result.enqueuedAt());
        assertEquals(testSubject.lastTouched(), result.lastTouched());
        assertEquals(expectedDiagnostics, result.diagnostics());
    }
}
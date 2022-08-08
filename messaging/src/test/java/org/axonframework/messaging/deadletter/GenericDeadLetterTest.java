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
        ThrowableCause testCause = new ThrowableCause(new RuntimeException("just because"));
        Instant testEnqueuedAt = Instant.now();
        Instant testLastTouched = Instant.now();
        MetaData testDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(
                SEQUENCE_IDENTIFIER, MESSAGE, testCause, testEnqueuedAt, testLastTouched, testDiagnostics
        );

        assertEquals(MESSAGE, testSubject.message());
        Optional<Cause> resultCause = testSubject.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());
        assertEquals(testEnqueuedAt, testSubject.enqueuedAt());
        assertEquals(testLastTouched, testSubject.lastTouched());
        assertEquals(testDiagnostics, testSubject.diagnostics());
    }

    @Test
    void testMarkTouched() {
        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        Instant expectedLastTouched = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedLastTouched, ZoneId.systemDefault());
        DeadLetter<EventMessage<String>> result = testSubject.markTouched();

        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(expectedLastTouched, testSubject.lastTouched());
        assertEquals(result.diagnostics(), testSubject.diagnostics());
    }

    @Test
    void testWithCauseAndNoOriginalCause() {
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(testThrowable);

        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostics(), testSubject.diagnostics());
    }

    @Test
    void testWithCauseAndOriginalCause() {
        Throwable originalThrowable = new RuntimeException("some other issue");
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE, originalThrowable);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(testThrowable);

        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostics(), testSubject.diagnostics());
    }

    @Test
    void testWithNullCauseAndNoOriginalCause() {
        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertEquals(result.message(), testSubject.message());
        assertFalse(result.cause().isPresent());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostics(), testSubject.diagnostics());
    }

    @Test
    void testWithNullCauseAndOriginalCause() {
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE, testThrowable);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostics(), testSubject.diagnostics());
    }

    @Test
    void testWithDiagnostics() {
        MetaData expectedDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withDiagnostics(expectedDiagnostics);

        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostics(), expectedDiagnostics);
    }

    @Test
    void testWithDiagnosticsBuilder() {
        MetaData expectedDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withDiagnostics(original -> original.and("key", "value"));

        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostics(), expectedDiagnostics);
    }
}
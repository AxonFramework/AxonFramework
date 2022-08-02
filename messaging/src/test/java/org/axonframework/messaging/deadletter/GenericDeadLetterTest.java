package org.axonframework.messaging.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.deadletter.EventSequenceIdentifier;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.provider.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericDeadLetter}.
 *
 * @author Steven van Beelen
 */
class GenericDeadLetterTest {

    private final SequenceIdentifier testSequenceIdentifier = new EventSequenceIdentifier("sequenceId", "group");
    private final EventMessage<String> testMessage = GenericEventMessage.asEventMessage("payload");

    @Test
    void testConstructForIdentifierAndMessage() {
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(testSequenceIdentifier, testMessage);

        assertEquals(testSequenceIdentifier, testSubject.sequenceIdentifier());
        assertEquals(testMessage, testSubject.message());
        assertFalse(testSubject.cause().isPresent());
        assertEquals(expectedTime, testSubject.enqueuedAt());
        assertEquals(expectedTime, testSubject.lastTouched());
        assertTrue(testSubject.diagnostic().isEmpty());
    }

    @Test
    void testConstructForIdentifierMessageAndThrowable() {
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(testSequenceIdentifier, testMessage, testThrowable);

        assertEquals(testSequenceIdentifier, testSubject.sequenceIdentifier());
        assertEquals(testMessage, testSubject.message());
        Optional<Cause> resultCause = testSubject.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(expectedTime, testSubject.enqueuedAt());
        assertEquals(expectedTime, testSubject.lastTouched());
        assertTrue(testSubject.diagnostic().isEmpty());
    }

    @Test
    void testConstructCompletelyManual() {
        String testIdentifier = "identifier";
        ThrowableCause testCause = new ThrowableCause(new RuntimeException("just because"));
        Instant testEnqueuedAt = Instant.now();
        Instant testLastTouched = Instant.now();
        MetaData testDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(testIdentifier,
                                                                               testSequenceIdentifier,
                                                                               testMessage,
                                                                               testCause,
                                                                               testEnqueuedAt,
                                                                               testLastTouched,
                                                                               testDiagnostics);

        assertEquals(testIdentifier, testSubject.identifier());
        assertEquals(testSequenceIdentifier, testSubject.sequenceIdentifier());
        assertEquals(testMessage, testSubject.message());
        Optional<Cause> resultCause = testSubject.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());
        assertEquals(testEnqueuedAt, testSubject.enqueuedAt());
        assertEquals(testLastTouched, testSubject.lastTouched());
        assertEquals(testDiagnostics, testSubject.diagnostic());
    }

    @Test
    void testWithCauseAndNoOriginalCause() {
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(testSequenceIdentifier, testMessage);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(testThrowable);

        assertEquals(result.identifier(), testSubject.identifier());
        assertEquals(result.sequenceIdentifier(), testSubject.sequenceIdentifier());
        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostic(), testSubject.diagnostic());
    }

    @Test
    void testWithCauseAndOriginalCause() {
        Throwable originalThrowable = new RuntimeException("some other issue");
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(testSequenceIdentifier, testMessage, originalThrowable);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(testThrowable);

        assertEquals(result.identifier(), testSubject.identifier());
        assertEquals(result.sequenceIdentifier(), testSubject.sequenceIdentifier());
        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostic(), testSubject.diagnostic());
    }

    @Test
    void testWithNullCauseAndNoOriginalCause() {
        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(testSequenceIdentifier, testMessage);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertEquals(result.identifier(), testSubject.identifier());
        assertEquals(result.sequenceIdentifier(), testSubject.sequenceIdentifier());
        assertEquals(result.message(), testSubject.message());
        assertFalse(result.cause().isPresent());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostic(), testSubject.diagnostic());
    }

    @Test
    void testWithNullCauseAndOriginalCause() {
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(testSequenceIdentifier, testMessage, testThrowable);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertEquals(result.identifier(), testSubject.identifier());
        assertEquals(result.sequenceIdentifier(), testSubject.sequenceIdentifier());
        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertTrue(resultCause.isPresent());
        assertEquals(expectedCause, resultCause.get());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostic(), testSubject.diagnostic());
    }

    @Test
    void testAndDiagnostics() {
        MetaData expectedDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(testSequenceIdentifier, testMessage);

        DeadLetter<EventMessage<String>> result = testSubject.andDiagnostics(expectedDiagnostics);

        assertEquals(result.identifier(), testSubject.identifier());
        assertEquals(result.sequenceIdentifier(), testSubject.sequenceIdentifier());
        assertEquals(result.message(), testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertFalse(resultCause.isPresent());
        assertEquals(result.enqueuedAt(), testSubject.enqueuedAt());
        assertEquals(result.lastTouched(), testSubject.lastTouched());
        assertEquals(result.diagnostic(), expectedDiagnostics);
    }

    // TODO: 02-08-2022 nice idea, but doesn't work as the Message isn't serializable through Jackson at the moment.
    @MethodSource("serializers")
    //@ParameterizedTest
    void testSerializationOfGenericDeadLetter(TestSerializer serializer) {
        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(testSequenceIdentifier, testMessage);

        DeadLetter<EventMessage<String>> result = serializer.serializeDeserialize(testSubject);

        assertEquals(testSubject, result);
    }

    static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }
}
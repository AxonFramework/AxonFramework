package org.axonframework.messaging.deadletter;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link ShouldEnqueue}. Either constructed through the constructors or through the
 * {@link Decisions} utility class.
 *
 * @author Steven van Beelen
 */
class ShouldEnqueueTest {

    private DeadLetter<? extends Message<?>> testLetter;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        testLetter = new GenericDeadLetter<>("seqId", GenericEventMessage.asEventMessage("payload"));
    }

    @Test
    void constructorShouldEnqueueAllowsEnqueueing() {
        ShouldEnqueue<Message<?>> testSubject = new ShouldEnqueue<>();

        assertTrue(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void decisionsEnqueueAllowsEnqueueing() {
        ShouldEnqueue<Message<?>> testSubject = Decisions.enqueue();

        assertTrue(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void constructorShouldEnqueueWithCauseAllowsEnqueueingWithGivenCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<Message<?>> testSubject = new ShouldEnqueue<>(testCause);

        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void decisionsEnqueueWithCauseAllowsEnqueueingWithGivenCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<Message<?>> testSubject = Decisions.enqueue(testCause);

        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void decisionsRequeueWithCauseAllowsEnqueueingWithGivenCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<Message<?>> testSubject = Decisions.requeue(testCause);

        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void constructorShouldEnqueueWithCauseAndDiagnosticsAllowsEnqueueingWithGivenCauseAndDiagnostics() {
        Throwable testCause = new RuntimeException("just because");
        MetaData testMetaData = MetaData.with("key", "value");

        ShouldEnqueue<Message<?>> testSubject = new ShouldEnqueue<>(testCause, letter -> testMetaData);

        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter.message(), result.message());
        assertEquals(testLetter.cause(), result.cause());
        assertEquals(testLetter.enqueuedAt(), result.enqueuedAt());
        assertEquals(testLetter.lastTouched(), result.lastTouched());
        assertEquals(testMetaData, result.diagnostics());
    }

    @Test
    void decisionsRequeueWithCauseAndDiagnosticsAllowsEnqueueingWithGivenCauseAndDiagnostics() {
        Throwable testCause = new RuntimeException("just because");
        MetaData testMetaData = MetaData.with("key", "value");

        ShouldEnqueue<Message<?>> testSubject = Decisions.requeue(testCause, letter -> testMetaData);

        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter.message(), result.message());
        assertEquals(testLetter.cause(), result.cause());
        assertEquals(testLetter.enqueuedAt(), result.enqueuedAt());
        assertEquals(testLetter.lastTouched(), result.lastTouched());
        assertEquals(testMetaData, result.diagnostics());
    }
}
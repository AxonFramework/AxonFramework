package org.axonframework.messaging.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link DoNotEnqueue}. Either constructed through the constructor or through the
 * {@link Decisions} utility class.
 *
 * @author Steven van Beelen
 */
class DoNotEnqueueTest {

    private DeadLetter<EventMessage<String>> testLetter;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        testLetter = new GenericDeadLetter<>("seqId", GenericEventMessage.asEventMessage("payload"));
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
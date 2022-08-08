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
 * Test class validating {@link IgnoreDecision}. Either constructed through the constructor or through the
 * {@link Decisions} utility class.
 *
 * @author Steven van Beelen
 */
class IgnoreDecisionTest {

    private DeadLetter<EventMessage<String>> testLetter;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        testLetter = new GenericDeadLetter<>("seqId", GenericEventMessage.asEventMessage("payload"));
    }

    @Test
    void testDefaultIgnoreDecision() {
        IgnoreDecision<Message<?>> testSubject = new IgnoreDecision<>();

        assertTrue(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void testDecisionsIgnore() {
        IgnoreDecision<Message<?>> testSubject = Decisions.ignore();

        assertTrue(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }
}
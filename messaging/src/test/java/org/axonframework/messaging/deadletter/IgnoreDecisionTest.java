package org.axonframework.messaging.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.deadletter.EventSequenceIdentifier;
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

        testLetter = new GenericDeadLetter<>(
                new EventSequenceIdentifier("seqId", "group"),
                GenericEventMessage.asEventMessage("payload")
        );
    }

    @Test
    void testDefaultIgnoreDecision() {
        IgnoreDecision<DeadLetter<? extends Message<?>>> testSubject = new IgnoreDecision<>();

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void testDecisionsIgnore() {
        IgnoreDecision<DeadLetter<? extends Message<?>>> testSubject = Decisions.ignore();

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }
}
package org.axonframework.messaging.deadletter;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link ThrowableCause}.
 *
 * @author Steven van Beelen
 */
class ThrowableCauseTest {

    @Test
    void constructThrowableCauseWithThrowable() {
        Throwable testThrowable = new RuntimeException("just because");

        ThrowableCause testSubject = new ThrowableCause(testThrowable);

        assertEquals(testThrowable.getClass().getName(), testSubject.type());
        assertEquals(testThrowable.getMessage(), testSubject.message());
    }

    @Test
    void constructThrowableCauseWithTypeAndMessage() {
        String testType = "type";
        String testMessage = "message";

        ThrowableCause testSubject = new ThrowableCause(testType, testMessage);

        assertEquals(testType, testSubject.type());
        assertEquals(testMessage, testSubject.message());
    }
}
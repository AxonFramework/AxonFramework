package org.axonframework.messaging.deadletter;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link ThrowableCause}.
 *
 * @author Steven van Beelen
 */
class ThrowableCauseTest {

    @Test
    void testConstructThrowableCauseWithThrowable() {
        Throwable testThrowable = new RuntimeException("just because");

        ThrowableCause testSubject = new ThrowableCause(testThrowable);

        assertEquals(testThrowable.getClass().getName(), testSubject.type());
        assertEquals(testThrowable.getMessage(), testSubject.message());
    }

    @Test
    void testConstructThrowableCauseWithTypeAndMessage() {
        String testType = "type";
        String testMessage = "message";

        ThrowableCause testSubject = new ThrowableCause(testType, testMessage);

        assertEquals(testType, testSubject.type());
        assertEquals(testMessage, testSubject.message());
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void testSerializationOfThrowableCause(TestSerializer serializer) {
        Throwable testThrowable = new RuntimeException("just because");

        ThrowableCause testSubject = new ThrowableCause(testThrowable);

        ThrowableCause result = serializer.serializeDeserialize(testSubject);

        assertEquals(testSubject.type(), result.type());
        assertEquals(testSubject.message(), result.message());
    }

    static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }
}
package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.ErrorMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.serialization.SerializationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Author: marc
 */
class ExceptionSerializerTest {
    @Test
    void serializeNullClient() {
        ErrorMessage result = ExceptionSerializer.serialize(null,
                                                            new RuntimeException(
                                                                    "Something went wrong"));
        assertEquals("", result.getLocation());
    }

    @Test
    void serializeNonNullClient() {
        ErrorMessage result = ExceptionSerializer.serialize("Client",
                                                            new RuntimeException(
                                                                    "Something went wrong"));
        assertEquals("Client", result.getLocation());
    }

    @Test
    void isExplicitlyNonTransientForTransientExceptions() {
        NoHandlerForCommandException transientException = new NoHandlerForCommandException("No handler message");
        assertFalse(ExceptionSerializer.isExplicitlyNonTransient(transientException));
    }

    @Test
    void isExplicitlyNonTransientForRegularExceptions() {
        RuntimeException runtimeException = new RuntimeException("Something went wrong");
        assertFalse(ExceptionSerializer.isExplicitlyNonTransient(runtimeException));
    }

    @Test
    void isExplicitlyNonTransientForNonTransientExceptions() {
        SerializationException nonTransientException = new SerializationException("Serialization error");
        assertTrue(ExceptionSerializer.isExplicitlyNonTransient(nonTransientException));
    }

    @Test
    void isExplicitlyNonTransientForNestedNonTransientException() {
        SerializationException nonTransientException = new SerializationException("Serialization error");
        RuntimeException nestedRuntimeException = new RuntimeException("Something went wrong nested", nonTransientException);
        RuntimeException baseRuntimeException = new RuntimeException("Something went wrong", nestedRuntimeException);

        assertTrue(ExceptionSerializer.isExplicitlyNonTransient(baseRuntimeException));
    }
}
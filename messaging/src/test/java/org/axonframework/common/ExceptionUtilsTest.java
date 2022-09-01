package org.axonframework.common;

import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.serialization.SerializationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Stefan Andjelkovic
 */
class ExceptionUtilsTest {
    @Test
    void isExplicitlyNonTransientForTransientExceptions() {
        NoHandlerForCommandException transientException = new NoHandlerForCommandException("No handler message");
        assertFalse(ExceptionUtils.isExplicitlyNonTransient(transientException));
    }

    @Test
    void isExplicitlyNonTransientForRegularExceptions() {
        RuntimeException runtimeException = new RuntimeException("Something went wrong");
        assertFalse(ExceptionUtils.isExplicitlyNonTransient(runtimeException));
    }

    @Test
    void isExplicitlyNonTransientForNonTransientExceptions() {
        SerializationException nonTransientException = new SerializationException("Serialization error");
        assertTrue(ExceptionUtils.isExplicitlyNonTransient(nonTransientException));
    }

    @Test
    void isExplicitlyNonTransientForNestedNonTransientException() {
        SerializationException nonTransientException = new SerializationException("Serialization error");
        RuntimeException nestedRuntimeException = new RuntimeException("Something went wrong nested", nonTransientException);
        RuntimeException baseRuntimeException = new RuntimeException("Something went wrong", nestedRuntimeException);

        assertTrue(ExceptionUtils.isExplicitlyNonTransient(baseRuntimeException));
    }
}
package org.axonframework.common;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdentifierValidatorTest {

    private final IdentifierValidator validator = IdentifierValidator.getInstance();

    @Test
    public void boxedPrimitivesAreValidIdentifiers() {
        assertTrue(validator.isValidIdentifier(Long.class));
        assertTrue(validator.isValidIdentifier(Integer.class));
        assertTrue(validator.isValidIdentifier(Double.class));
        assertTrue(validator.isValidIdentifier(Short.class));
    }

    @Test
    public void stringIsValidIdentifier() {
        assertTrue(validator.isValidIdentifier(CharSequence.class));
    }

    @Test
    public void typeWithoutToStringIsNotAccepted() {
        assertFalse(validator.isValidIdentifier(CustomType.class));
    }

    @Test
    public void typeWithOverriddenToString() {
        assertTrue(validator.isValidIdentifier(CustomType2.class));
    }

    private static class CustomType {
    }

    private static class CustomType2 {
        @Override
        public String toString() {
            return "ok";
        }
    }
}
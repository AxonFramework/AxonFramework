/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentifierValidatorTest {

    private final IdentifierValidator validator = IdentifierValidator.getInstance();

    @Test
    void boxedPrimitivesAreValidIdentifiers() {
        assertTrue(validator.isValidIdentifier(Long.class));
        assertTrue(validator.isValidIdentifier(Integer.class));
        assertTrue(validator.isValidIdentifier(Double.class));
        assertTrue(validator.isValidIdentifier(Short.class));
    }

    @Test
    void stringIsValidIdentifier() {
        assertTrue(validator.isValidIdentifier(CharSequence.class));
    }

    @Test
    void typeWithoutToStringIsNotAccepted() {
        assertFalse(validator.isValidIdentifier(CustomType.class));
    }

    @Test
    void typeWithOverriddenToString() {
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
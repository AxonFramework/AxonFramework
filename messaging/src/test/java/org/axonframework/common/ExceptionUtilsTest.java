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
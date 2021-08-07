/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerExecutionExceptionTest {

    @Test
    void testResolveDetailsFromNestedExecutionException() {
        Exception exception = new RuntimeException(new StubHandlerExecutionException("test", null, "Details!"));

        assertEquals("Details!", HandlerExecutionException.resolveDetails(exception).orElse(null));
    }

    @Test
    void testResolveDetailsFromExecutionException() {
        Exception exception = new StubHandlerExecutionException("test", null, "Details!");

        assertEquals("Details!", HandlerExecutionException.resolveDetails(exception).orElse(null));
    }

    @Test
    void testResolveDetailsFromNull() {
        assertFalse(HandlerExecutionException.resolveDetails(null).isPresent());
    }

    @Test
    void testResolveDetailsFromRuntimeException() {
        assertFalse(HandlerExecutionException.resolveDetails(new RuntimeException()).isPresent());
    }

    @Test
    void testStackTracePresence() {
        Exception exception = new StubHandlerExecutionException("Some message");
        assertEquals(0, exception.getStackTrace().length);

        exception = new StubHandlerExecutionException("Some message", new RuntimeException());
        assertEquals(0, exception.getStackTrace().length);

        exception = new StubHandlerExecutionException("Some message", new RuntimeException(), "Some details");
        assertEquals(0, exception.getStackTrace().length);

        exception = new StubHandlerExecutionException("Some message", new RuntimeException(), "Some details", false);
        assertEquals(0, exception.getStackTrace().length);

        exception = new StubHandlerExecutionException("Some message", new RuntimeException(), "Some details", true);
        assertTrue(exception.getStackTrace().length > 0);
    }

    private static class StubHandlerExecutionException extends HandlerExecutionException {

        public StubHandlerExecutionException(String message) {
            super(message);
        }

        public StubHandlerExecutionException(String message, Throwable cause) {
            super(message, cause);
        }

        public StubHandlerExecutionException(String message, Throwable cause, Object details) {
            super(message, cause, details);
        }

        public StubHandlerExecutionException(String message, Throwable cause, Object details, boolean writableStackTrace) {
            super(message, cause, details, writableStackTrace);
        }
    }
}

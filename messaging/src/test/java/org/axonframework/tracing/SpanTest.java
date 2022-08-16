/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.tracing;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class SpanTest {

    private TestSpan span = new TestSpan();

    @Test
    void testRunRunnableWorks() {
        span.run(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
        });
        assertTrue(span.ended);
    }

    @Test
    void testRunRunnableRegistersException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        assertThrows(RuntimeException.class, () -> span.run(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            throw exception;
        }));
        assertTrue(span.ended);
        assertEquals(exception, span.exception);
    }

    @Test
    void testWrapRunnableWorks() {
        span.wrapRunnable(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
        }).run();
        assertTrue(span.ended);
    }

    @Test
    void testWrapRunnableRegistersException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        Runnable wrappedRunnable = span.wrapRunnable(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            throw exception;
        });
        assertThrows(RuntimeException.class, wrappedRunnable::run);
        assertTrue(span.ended);
        assertEquals(exception, span.exception);
    }

    @Test
    void testRunSupplierWorks() {
        String result = span.runSupplier(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            return "SupplyString";
        });
        assertTrue(span.ended);
        assertEquals("SupplyString", result);
    }

    @Test
    void testRunSupplierRegistersException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        assertThrows(RuntimeException.class, () -> span.runSupplier(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            throw exception;
        }));
        assertTrue(span.ended);
        assertEquals(exception, span.exception);
    }

    @Test
    void testWrapSupplierWorks() {
        String result = span.wrapSupplier(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            return "SupplyString";
        }).get();
        assertTrue(span.ended);
        assertEquals("SupplyString", result);
    }

    @Test
    void testWrapSupplierRegistersException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        Supplier<Object> wrappedSupplier = span.wrapSupplier(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            throw exception;
        });
        assertThrows(RuntimeException.class, wrappedSupplier::get);
        assertTrue(span.ended);
        assertEquals(exception, span.exception);
    }

    @Test
    void testRunCallableWorks() throws Exception {
        String result = span.runCallable(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            return "CallString";
        });
        assertTrue(span.ended);
        assertEquals("CallString", result);
    }

    @Test
    void testRunCallableRegistersRuntimeException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        assertThrows(RuntimeException.class, () -> span.runCallable(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            throw exception;
        }));
        assertTrue(span.ended);
        assertEquals(exception, span.exception);
    }

    @Test
    void testRunCallableRegistersCheckedException() {
        IOException exception = new IOException("My custom exception");
        assertThrows(IOException.class, () -> span.runCallable(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            throw exception;
        }));
        assertTrue(span.ended);
        assertEquals(exception, span.exception);
    }

    @Test
    void testWrapCallableWorks() throws Exception {
        String result = span.wrapCallable(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            return "CallString";
        }).call();
        assertTrue(span.ended);
        assertEquals("CallString", result);
    }

    @Test
    void testWrapCallableRegistersRuntimeException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        Callable<Object> wrappedCallable = span.wrapCallable(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            throw exception;
        });
        assertThrows(RuntimeException.class, wrappedCallable::call);
        assertTrue(span.ended);
        assertEquals(exception, span.exception);
    }

    @Test
    void testWrapCallableRegistersCheckedException() {
        IOException exception = new IOException("My custom exception");
        Callable<Object> wrappedCallable = span.wrapCallable(() -> {
            assertTrue(span.started);
            assertFalse(span.ended);
            throw exception;
        });
        assertThrows(IOException.class, wrappedCallable::call);
        assertTrue(span.ended);
        assertEquals(exception, span.exception);
    }

    static class TestSpan implements Span {

        boolean started;
        boolean ended;
        Throwable exception;

        @Override
        public Span start() {
            this.started = true;
            return this;
        }

        @Override
        public void end() {
            this.ended = true;
        }

        @Override
        public Span recordException(Throwable t) {
            this.exception = t;
            return this;
        }
    }
}

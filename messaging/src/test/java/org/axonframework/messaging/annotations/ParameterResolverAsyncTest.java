/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.annotations;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the default async parameter resolution in {@link ParameterResolver}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class ParameterResolverAsyncTest {

    private ProcessingContext context;

    @BeforeEach
    void setUp() {
        context = new StubProcessingContext();
    }

    @Nested
    class DefaultAsyncMethod {

        @Test
        void wrapsSuccessfulSynchronousResolutionInCompletedFuture() throws ExecutionException, InterruptedException {
            // given
            ParameterResolver<String> resolver = new TestParameterResolver("testValue");

            // when
            CompletableFuture<String> future = resolver.resolveParameterValueAsync(context);

            // then
            assertTrue(future.isDone(), "Future should be completed immediately");
            assertFalse(future.isCompletedExceptionally(), "Future should not be completed exceptionally");
            assertEquals("testValue", future.get());
        }

        @Test
        void wrapsNullReturnValueInCompletedFuture() throws ExecutionException, InterruptedException {
            // given
            ParameterResolver<String> resolver = new TestParameterResolver(null);

            // when
            CompletableFuture<String> future = resolver.resolveParameterValueAsync(context);

            // then
            assertTrue(future.isDone(), "Future should be completed immediately");
            assertFalse(future.isCompletedExceptionally(), "Future should not be completed exceptionally");
            assertNull(future.get());
        }

        @Test
        void propagatesExceptionFromSynchronousResolution() {
            // given
            RuntimeException expectedException = new RuntimeException("Test exception");
            ParameterResolver<String> resolver = new ThrowingParameterResolver(expectedException);

            // when
            CompletableFuture<String> future = resolver.resolveParameterValueAsync(context);

            // then
            assertTrue(future.isDone(), "Future should be completed immediately");
            assertTrue(future.isCompletedExceptionally(), "Future should be completed exceptionally");
            ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
            assertSame(expectedException, executionException.getCause());
        }
    }

    @Nested
    class CustomAsyncImplementation {

        @Test
        void allowsOverridingWithTrueAsyncBehavior() throws ExecutionException, InterruptedException {
            // given
            ParameterResolver<String> resolver = new CustomAsyncParameterResolver("asyncValue");

            // when
            CompletableFuture<String> future = resolver.resolveParameterValueAsync(context);

            // then
            assertTrue(future.isDone(), "Future should complete (in this test immediately)");
            assertEquals("asyncValue", future.get());
        }

        @Test
        void customAsyncImplementationCanReturnDelayedFuture() {
            // given
            CompletableFuture<String> delayedFuture = new CompletableFuture<>();
            ParameterResolver<String> resolver = new DelayedAsyncParameterResolver(delayedFuture);

            // when
            CompletableFuture<String> future = resolver.resolveParameterValueAsync(context);

            // then
            assertFalse(future.isDone(), "Future should not be completed yet");

            // when - complete the delayed future
            delayedFuture.complete("delayedValue");

            // then
            assertTrue(future.isDone(), "Future should now be completed");
            assertEquals("delayedValue", future.getNow(null));
        }
    }

    // Test implementations

    private static class TestParameterResolver implements ParameterResolver<String> {

        private final String value;

        TestParameterResolver(String value) {
            this.value = value;
        }

        @Nullable
        @Override
        public String resolveParameterValue(@Nonnull ProcessingContext context) {
            return value;
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return true;
        }
    }

    private static class ThrowingParameterResolver implements ParameterResolver<String> {

        private final RuntimeException exception;

        ThrowingParameterResolver(RuntimeException exception) {
            this.exception = exception;
        }

        @Nullable
        @Override
        public String resolveParameterValue(@Nonnull ProcessingContext context) {
            throw exception;
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return true;
        }
    }

    private static class CustomAsyncParameterResolver implements ParameterResolver<String> {

        private final String value;

        CustomAsyncParameterResolver(String value) {
            this.value = value;
        }

        @Nullable
        @Override
        public String resolveParameterValue(@Nonnull ProcessingContext context) {
            return value;
        }

        @Nonnull
        @Override
        public CompletableFuture<String> resolveParameterValueAsync(@Nonnull ProcessingContext context) {
            return CompletableFuture.completedFuture(value);
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return true;
        }
    }

    private static class DelayedAsyncParameterResolver implements ParameterResolver<String> {

        private final CompletableFuture<String> future;

        DelayedAsyncParameterResolver(CompletableFuture<String> future) {
            this.future = future;
        }

        @Nullable
        @Override
        public String resolveParameterValue(@Nonnull ProcessingContext context) {
            return future.join();
        }

        @Nonnull
        @Override
        public CompletableFuture<String> resolveParameterValueAsync(@Nonnull ProcessingContext context) {
            return future;
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return true;
        }
    }
}

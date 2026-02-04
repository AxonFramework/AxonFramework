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

package org.axonframework.messaging.core.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the async parameter resolution in {@link ParameterResolver}.
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
    class CustomAsyncImplementation {

        @Test
        void allowsOverridingWithTrueAsyncBehavior() throws ExecutionException, InterruptedException {
            // given
            ParameterResolver<String> resolver = new CustomAsyncParameterResolver("asyncValue");

            // when
            CompletableFuture<String> future = resolver.resolveParameterValue(context);

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
            CompletableFuture<String> future = resolver.resolveParameterValue(context);

            // then
            assertFalse(future.isDone(), "Future should not be completed yet");

            // when - complete the delayed future
            delayedFuture.complete("delayedValue");

            // then
            assertTrue(future.isDone(), "Future should now be completed");
            assertEquals("delayedValue", future.getNow(null));
        }
    }

    private static class CustomAsyncParameterResolver implements ParameterResolver<String> {

        private final String value;

        CustomAsyncParameterResolver(String value) {
            this.value = value;
        }

        @Nonnull
        @Override
        public CompletableFuture<String> resolveParameterValue(@Nonnull ProcessingContext context) {
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

        @Nonnull
        @Override
        public CompletableFuture<String> resolveParameterValue(@Nonnull ProcessingContext context) {
            return future;
        }

        @Override
        public boolean matches(@Nonnull ProcessingContext context) {
            return true;
        }
    }
}

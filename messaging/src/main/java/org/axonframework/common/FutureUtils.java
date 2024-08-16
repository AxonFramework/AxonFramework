/*
 * Copyright (c) 2010-2024. Axon Framework
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

/**
 * Utility class containing reusable functionality for interacting with the {@link CompletableFuture}.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public abstract class FutureUtils {

    private FutureUtils() {
        // Utility class
    }

    /**
     * Utility method that doesn't do anything. Its purpose is to simplify conversion of a {@link CompletableFuture}
     * with any generic type to a {@code CompletableFuture<Void>}.
     * <p>
     * Example: {@code completableFuture.thenApply(FutureUtils::ignoreResult}
     *
     * @param toIgnore The actual result of the {@link CompletableFuture}.
     * @param <T>      The declared result of the {@link CompletableFuture} to ignore the result of.
     * @return {@code null}, as that's the only valid value for {@link Void}.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static <T> Void ignoreResult(@SuppressWarnings("unused") T toIgnore) {
        return null;
    }

    /**
     * Creates a completed {@link CompletableFuture} with {@code null} as result.
     *
     * @param <T> The declared type to return in the {@link CompletableFuture}.
     * @return A {@link CompletableFuture} that is completed with {@code null}.
     */
    public static <T> CompletableFuture<T> emptyCompletedFuture() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Creates a function that can be passed to a {@link CompletableFuture#whenComplete(BiConsumer)} or
     * {@link CompletableFuture#whenCompleteAsync(BiConsumer)} invocation, to complete the given {@code future} with the
     * same result as the {@link CompletableFuture} that this function is passed to.
     *
     * @param future The {@link CompletableFuture} to also complete.
     * @param <T>    The declared type of result from the {@link CompletableFuture}.
     * @return A function that completes another {@code future} with the same results.
     */
    public static <T> BiConsumer<T, Throwable> alsoComplete(CompletableFuture<T> future) {
        return (r, e) -> {
            if (e == null) {
                future.complete(r);
            } else {
                future.completeExceptionally(e);
            }
        };
    }

    /**
     * Unwrap given {@code exception} from the exception-wrappers added by {@link CompletableFuture}.
     * <p>
     * More specifically, if the given {@code exception} is a {@link CompletionException} or {@link ExecutionException},
     * it returns the cause. Otherwise, it will return the exception as-is.
     *
     * @param exception The exception to unwrap.
     * @return The unwrapped exception if the given {@code exception} is of type {@link CompletionException} or
     * {@link ExecutionException}. Otherwise, it is returned as is.
     */
    public static Throwable unwrap(Throwable exception) {
        return exception instanceof CompletionException || exception instanceof ExecutionException
                ? exception.getCause()
                : exception;
    }

    /**
     *
     * @param exception
     * @return
     */
    public static String unwrapMessage(Throwable exception) {
        return unwrap(exception).getMessage();
    }
}

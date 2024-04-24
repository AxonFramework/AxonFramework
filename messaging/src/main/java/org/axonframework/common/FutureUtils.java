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
 * TODO Add/enhance documentation as described in #2966.
 *
 * @author Allard Buijze
 */
public abstract class FutureUtils {

    private FutureUtils() {
    }

    /**
     * Utility method that doesn't do anything. Its purpose is to simplify conversion of a {@code CompletableFuture}
     * with any generic type to a {@code CompletableFuture<Void>}.
     * <p>
     * Example: {@code completableFuture.thenApply(FutureUtils::ignoreResult}
     *
     * @param toIgnore the actual result of the completable future
     * @param <T>      the declared result of the completable future to ignore the result of
     * @return {@code null}, as that's the only valid value for Void
     */
    @SuppressWarnings("UnusedReturnValue")
    public static <T> Void ignoreResult(@SuppressWarnings("unused") T toIgnore) {
        return null;
    }

    public static <T> CompletableFuture<T> emptyCompletedFuture() {
        return CompletableFuture.completedFuture(null);
    }

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
     * Unwrap given {@code exception} from wrappers added by CompletableFuture. More specifically, if the given
     * {@code exception} is a {@link CompletionException} or {@link ExecutionException}, it returns the cause.
     * Otherwise, it will return the exception as-is.
     *
     * @param exception The exception to unwrap
     * @return the unwrapped exception
     */
    public static Throwable unwrap(Throwable exception) {
        if (exception instanceof CompletionException || exception instanceof ExecutionException) {
            return exception.getCause();
        }
        return exception;
    }
}

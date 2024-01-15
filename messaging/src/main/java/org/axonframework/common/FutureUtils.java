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

public abstract class FutureUtils {

    private FutureUtils() {
    }

    /**
     * Utility method that doesn't do anything. It's purpose is to simplify conversion of a {@code CompletableFuture}
     * with any generic type to a {@code CompletableFuture<Void>}.
     * <p>
     * Example: {@code completableFuture.thenApply(ObjectUtils::ignoreResult}
     *
     * @param toIgnore the actual result of the completable future
     * @param <T>      the declared result of the completable future to ignore the result of
     * @return {@code null}, as that's the only valid value for Void
     */
    @SuppressWarnings("UnusedReturnValue")
    public static <T> Void ignoreResult(@SuppressWarnings("unused") T toIgnore) {
        return null;
    }

    public static CompletableFuture<Void> emptyCompletedFuture() {
        return CompletableFuture.completedFuture(null);
    }
}

/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.common.function;

/**
 * Functional interface for operations which may throw a checked exception.
 *
 * @param <T> The input type of the function.
 * @param <R> The result type of the function.
 * @param <X> The exception type the function may throw.
 * @author John Hendrikx
 * @since 5.0.2
 */
public interface ThrowingFunction<T, R, X extends Exception> {

    /**
     * Applies the function to the given {@code T}.
     *
     * @param input The input of type {@code T}.
     * @return The result of applying the function.
     * @throws X When the function failed with an exception of type {@code X}.
     */
    R apply(T input) throws X;
}
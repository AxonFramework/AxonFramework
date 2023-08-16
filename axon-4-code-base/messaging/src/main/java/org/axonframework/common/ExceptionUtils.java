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

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Exception utility methods.
 *
 * @author Rene de Waele
 */
public abstract class ExceptionUtils {

    private ExceptionUtils() {
    }

    /**
     * Checks if the given {@code exception} matches the given {@code predicate}. If it does an optional with the
     * exception is returned, if not the cause of the exception is checked and returned if matching and so on until a
     * match is found. If neither the given exception nor any of its causes match the predicate an empty Optional is
     * returned.
     *
     * @param exception the exception to check
     * @param predicate the predicate to check the exception and its causes
     * @return an optional containing the exception matching the predicate or an empty optional if neither the given
     * exception nor any of its causes match the predicate
     */
    public static Optional<Throwable> findException(Throwable exception, Predicate<Throwable> predicate) {
        Throwable result = exception;
        while (result != null) {
            if (predicate.test(result)) {
                return Optional.of(result);
            } else {
                result = result.getCause();
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the given {@code exception} class is an instance of the given {@code exceptionClass}. If so an optional with the
     * exception is returned. If not the cause of the exception is checked and returned if matching and so on until a
     * match is found. If neither the given exception nor any of its causes match the predicate an empty Optional is
     * returned.
     *
     * @param exception the exception to check
     * @param exceptionClass the target exception class
     * @return an optional containing the exception matching the predicate or an empty optional if neither the given
     * exception nor any of its causes match the predicate
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> Optional<T> findException(Throwable exception, Class<T> exceptionClass) {
        return findException(exception, exceptionClass::isInstance).map(e -> (T) e);
    }

    /**
     * Indicates whether the given {@code failure} is clearly non-transient. Non-transient exceptions indicate
     * that the handling of the message will fail in the same way if retried
     *
     * @param failure the exception that occurred while processing a message
     * @return {@code true} if the exception is clearly non-transient
     */
    public static boolean isExplicitlyNonTransient(Throwable failure) {
        return failure instanceof AxonNonTransientException
                || (failure.getCause() != null && isExplicitlyNonTransient(failure.getCause()));
    }
}

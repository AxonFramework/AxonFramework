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

package org.axonframework.messaging;

import org.axonframework.common.AxonException;

import java.util.Optional;

/**
 * Base exception for exceptions raised by Handler methods. Besides standard exception information (such as message and
 * cause), these exception may optionally carry an object with additional application-specific details about the
 * exception.
 * <p/>
 * By default, a stack trace is not generated for this exception. However, the stack trace creation can be enforced
 * explicitly via the constructor accepting the {@code writableStackTrace} parameter.
 *
 * @author Allard Buize
 * @since 4.2
 */
public abstract class HandlerExecutionException extends AxonException {

    private final Object details;

    /**
     * Initializes an execution exception with given {@code message}. The cause and application-specific details are set
     * to {@code null}.
     *
     * @param message A message describing the exception
     */
    public HandlerExecutionException(String message) {
        this(message, null, null);
    }

    /**
     * Initializes an execution exception with given {@code message} and {@code cause}. The application-specific details
     * are set to {@code null}.
     *
     * @param message A message describing the exception
     * @param cause   the cause of the execution exception
     */
    public HandlerExecutionException(String message, Throwable cause) {
        this(message, cause, resolveDetails(cause).orElse(null));
    }

    /**
     * Initializes an execution exception with given {@code message}, {@code cause} and application-specific
     * {@code details}.
     *
     * @param message A message describing the exception
     * @param cause   The cause of the execution exception
     * @param details An object providing application-specific details of the exception
     */
    public HandlerExecutionException(String message, Throwable cause, Object details) {
        this(message, cause, details, false);
    }

    /**
     * Initializes an execution exception with given {@code message}, {@code cause}, application-specific
     * {@code details}, and {@code writableStackTrace}
     *
     * @param message            A message describing the exception
     * @param cause              The cause of the execution exception
     * @param details            An object providing application-specific details of the exception
     * @param writableStackTrace Whether the stack trace should be generated ({@code true}) or not ({@code false})
     */
    public HandlerExecutionException(String message, Throwable cause, Object details, boolean writableStackTrace) {
        super(message, cause, writableStackTrace);
        this.details = details;
    }

    /**
     * Resolve details from the given {@code throwable}, taking into account that the details may be available in any of
     * the {@code HandlerExecutionException}s is the "cause" chain.
     *
     * @param throwable The exception to resolve the details from
     * @param <R>       The type of details expected
     * @return an Optional containing details, if present in the given {@code throwable}
     */
    public static <R> Optional<R> resolveDetails(Throwable throwable) {
        if (throwable instanceof HandlerExecutionException) {
            return ((HandlerExecutionException) throwable).getDetails();
        } else if (throwable != null && throwable.getCause() != null) {
            return resolveDetails(throwable.getCause());
        }
        return Optional.empty();
    }

    /**
     * Returns an Optional containing application-specific details of the exception, if any were provided. These details
     * are implicitly cast to the expected type. A mismatch in type may lead to a {@link ClassCastException} further
     * downstream, when accessing the Optional's enclosed value.
     *
     * @param <R> The type of details expected
     * @return an Optional containing the details, if provided
     */
    @SuppressWarnings("unchecked")
    public <R> Optional<R> getDetails() {
        return Optional.ofNullable((R) details);
    }
}
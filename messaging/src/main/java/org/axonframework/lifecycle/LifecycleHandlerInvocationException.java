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

package org.axonframework.lifecycle;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Method;

import static java.lang.String.format;

/**
 * Exception indicating a failure occurred during a lifecycle handler method invocation.
 *
 * @author Steven van Beelen
 * @since 4.3.0
 */
public class LifecycleHandlerInvocationException extends RuntimeException {

    private static final String DEFAULT_FAILURE_MESSAGE =
            "Failed during invocation of lifecycle handler [%s] on component [%s]";

    /**
     * Instantiates an exception using the given {@code message} indicating a failure during a lifecycle handler method
     * invocation.
     *
     * @param message The message describing the exception.
     */
    public LifecycleHandlerInvocationException(@Nonnull String message) {
        super(message);
    }

    /**
     * Instantiates an exception using the given {@code lifecycleComponent}, {@code lifecycleHandler} and {@code cause},
     * indicating a failure during a lifecycle handler method invocation.
     *
     * @param lifecycleHandler   The {@link Method} in question which failed.
     * @param lifecycleComponent The {@link Object} of which the given {@code lifecycleHandler} was invoked
     *                           exceptionally.
     * @param cause              The underlying cause of the exception.
     */
    public LifecycleHandlerInvocationException(@Nonnull Method lifecycleHandler,
                                               @Nonnull Object lifecycleComponent,
                                               @Nullable Throwable cause) {
        this(format(DEFAULT_FAILURE_MESSAGE, lifecycleHandler, lifecycleComponent), cause);
    }

    /**
     * Instantiates an exception using the given {@code message} and {@code cause} indicating a failure during a
     * lifecycle handler method invocation.
     *
     * @param message The message describing the exception.
     * @param cause   The underlying cause of the exception.
     */
    public LifecycleHandlerInvocationException(@Nonnull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}

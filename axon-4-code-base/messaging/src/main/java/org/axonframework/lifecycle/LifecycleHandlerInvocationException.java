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

package org.axonframework.lifecycle;

import org.axonframework.common.AxonNonTransientException;

import java.lang.reflect.Method;

import static java.lang.String.format;

/**
 * Exception indicating a failure occurred during a lifecycle handler method invocation.
 *
 * @author Steven van Beelen
 * @see StartHandler
 * @see ShutdownHandler
 * @since 4.3
 */
public class LifecycleHandlerInvocationException extends AxonNonTransientException {

    private static final String DEFAULT_FAILURE_MESSAGE =
            "Failed during invocation of lifecycle handler [%s] on component [%s]";

    /**
     * Instantiates an exception using the given {@code message} indicating a failure during a lifecycle handler method
     * invocation.
     *
     * @param message the message describing the exception
     */
    public LifecycleHandlerInvocationException(String message) {
        super(message);
    }

    /**
     * Instantiates an exception using the given {@code lifecycleComponent}, {@code lifecycleHandler} and {@code cause},
     * indicating a failure during a lifecycle handler method invocation.
     *
     * @param lifecycleHandler   the {@link Method} in question which failed
     * @param lifecycleComponent the {@link Object} of which the given {@code lifecycleHandler} was invoked
     *                           exceptionally
     * @param cause              the underlying cause of the exception
     */
    public LifecycleHandlerInvocationException(Method lifecycleHandler, Object lifecycleComponent, Throwable cause) {
        this(format(DEFAULT_FAILURE_MESSAGE, lifecycleHandler, lifecycleComponent), cause);
    }

    /**
     * Instantiates an exception using the given {@code message} and {@code cause} indicating a failure during a
     * lifecycle handler method invocation.
     *
     * @param message the message describing the exception
     * @param cause   the underlying cause of the exception
     */
    public LifecycleHandlerInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}

/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.lifecycle;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating a process tried to register an activity whilst the application is shutting down.
 *
 * @author Steven van Beelen
 * @see ShutdownLatch
 * @since 4.3
 */
public class ShutdownInProgressException extends AxonNonTransientException {

    private static final String DEFAULT_MESSAGE = "Cannot start the activity, shutdown in progress";

    /**
     * Construct this exception with the default message {@code "Cannot start the activity, shutdown in progress"}.
     */
    public ShutdownInProgressException() {
        this(DEFAULT_MESSAGE);
    }

    /**
     * Constructs this exception with given {@code message} explaining the cause.
     *
     * @param message The message explaining the cause
     */
    public ShutdownInProgressException(String message) {
        super(message);
    }
}

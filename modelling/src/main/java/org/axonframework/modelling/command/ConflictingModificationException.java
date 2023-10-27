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

package org.axonframework.modelling.command;

import org.axonframework.common.AxonNonTransientException;

/**
 * Root of a hierarchy of exceptions indicating the detection of conflicting concurrent modifications. These conflicts
 * are typically detected when aggregates are loaded or saved while another action has made changes to them.
 * <p/>
 * This exception is non-transient, meaning that the the exception will occur when retrying the action. Typically, user
 * interaction or confirmation is needed before the failed action can be retried.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class ConflictingModificationException extends AxonNonTransientException {

    private static final long serialVersionUID = -4333021767907264897L;

    /**
     * Initializes the exception using the given {@code message}.
     *
     * @param message The message describing the exception
     */
    public ConflictingModificationException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given {@code message} and {@code cause}.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public ConflictingModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

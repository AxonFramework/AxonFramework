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

package org.axonframework.messaging;

import org.axonframework.common.AxonTransientException;

/**
 * Exception thrown to indicate that execution of a task has failed. Depending on the cause of the execution exception
 * the operation could succeed if repeated.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class ExecutionException extends AxonTransientException {

    /**
     * Initialize an ExecutionException with the given {@code message} and {@code cause}.
     *
     * @param message The message describing the cause of the exception
     * @param cause   The cause of the exception
     */
    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

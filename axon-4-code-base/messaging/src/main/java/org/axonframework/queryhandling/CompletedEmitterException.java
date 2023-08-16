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

package org.axonframework.queryhandling;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that {@link QueryUpdateEmitter} is completed, thus cannot be used to emit messages and report
 * errors.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class CompletedEmitterException extends AxonNonTransientException {

    /**
     * Initializes the exception with given {@code message}.
     *
     * @param message the message
     */
    public CompletedEmitterException(String message) {
        super(message);
    }
}

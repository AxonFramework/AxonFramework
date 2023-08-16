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

package org.axonframework.eventhandling;

import org.axonframework.common.AxonException;

/**
 * Exception thrown when an {@link EventProcessor} failed to handle a batch of events.
 *
 * @author Allard Buijze
 * @since 0.3
 */
public class EventProcessingException extends AxonException {

    /**
     * Initialize the exception with given {@code message} and {@code cause}.
     *
     * @param message Message describing the cause of the exception
     * @param cause   The exception that caused this exception to occur.
     */
    public EventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

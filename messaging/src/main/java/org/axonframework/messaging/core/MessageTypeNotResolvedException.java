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

package org.axonframework.messaging.core;

import org.axonframework.common.AxonException;

/**
 * Exception thrown when a {@link MessageTypeResolver} is unable to determine the {@link MessageType} for a given
 * payload type. This typically occurs when a resolver lacks a mapping for the requested type or when no compatible
 * resolver is available in the fallback chain.
 * <p>
 * This exception indicates a configuration issue where message types have not been properly registered for all
 * payloads that might be resolved.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MessageTypeNotResolvedException extends AxonException {

    /**
     * Initializes a new instance with the given error {@code message}.
     *
     * @param message The message describing the error that caused this exception.
     */
    public MessageTypeNotResolvedException(String message) {
        super(message);
    }
}

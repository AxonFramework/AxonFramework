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

package org.axonframework.messaging.annotation;

import org.axonframework.common.AxonException;

/**
 * MessageHandlerInvocationException is a runtime exception that wraps an exception thrown by an invoked message
 * handler.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class MessageHandlerInvocationException extends AxonException {

    private static final long serialVersionUID = 664867158607341533L;

    /**
     * Initialize the MessageHandlerInvocationException using given {@code message} and {@code cause}.
     *
     * @param message A message describing the cause of the exception
     * @param cause   The exception thrown by the Event Handler
     */
    public MessageHandlerInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}

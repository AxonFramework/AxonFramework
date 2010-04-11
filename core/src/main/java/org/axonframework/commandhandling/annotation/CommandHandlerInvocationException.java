/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.annotation;

/**
 * CommandHandlerInvocationException is a runtime exception that wraps an exception thrown by an invoked command
 * handler.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class CommandHandlerInvocationException extends RuntimeException {

    /**
     * Initialize the CommandHandlerInvocationException using given <code>message</code> and <code>cause</code>.
     *
     * @param message A message describing the cause of the exception
     * @param cause   The exception thrown by the Event Handler
     */
    public CommandHandlerInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}

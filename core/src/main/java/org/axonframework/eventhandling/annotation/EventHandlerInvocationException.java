/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.AxonException;

/**
 * EventHandlerInvocationException is a runtime exception that wraps an exception thrown by an invoked event handler.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class EventHandlerInvocationException extends AxonException {

    private static final long serialVersionUID = 664867158607341533L;

    /**
     * Initialize the EventHandlerInvocationException using given <code>message</code> and <code>cause</code>.
     *
     * @param message A message describing the cause of the exception
     * @param cause   The exception thrown by the Event Handler
     */
    public EventHandlerInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}

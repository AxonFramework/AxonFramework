/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.common.AxonNonTransientException;

/**
 * Exception indicating that the subscription of an Event Listener has not succeeded. Generally, it means that some
 * pre-conditions set by the Event Bus implementation for the listener have not been met.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class EventListenerSubscriptionFailedException extends AxonNonTransientException {

    private static final long serialVersionUID = 6422530844103473827L;

    /**
     * Initializes the exception with given descriptive <code>message</code>.
     *
     * @param message The message describing the cause of the error
     */
    public EventListenerSubscriptionFailedException(String message) {
        super(message);
    }

    /**
     * Initializes the exception with given descriptive <code>message</code> and originating <code>cause</code>.
     *
     * @param message The message describing the cause of the error
     * @param cause   The cause of the exception
     */
    public EventListenerSubscriptionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

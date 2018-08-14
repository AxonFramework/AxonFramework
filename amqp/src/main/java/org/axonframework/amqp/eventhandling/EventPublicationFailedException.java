/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.amqp.eventhandling;

import org.axonframework.common.AxonException;

/**
 * Exception indication that an error occurred while publishing an event to an AMQP Broker
 *
 * Deprecated in favor of {@link org.axonframework.messaging.EventPublicationFailedException}
 * @author Allard Buijze
 * @since 2.0
 */
@Deprecated
public class EventPublicationFailedException extends org.axonframework.messaging.EventPublicationFailedException {

    private static final long serialVersionUID = 3663633361627495227L;

    /**
     * Initialize the exception using given descriptive {@code message} and {@code cause}
     *
     * @param message A message describing the exception
     * @param cause   The exception describing the cause of the failure
     */
    public EventPublicationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

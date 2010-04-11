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

package org.axonframework.eventstore;

/**
 * Exception indicating that the event store could not find an event stream for a given aggregate type and identifier.
 * This typically means that there is no aggregate with the given identifier.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class EventStreamNotFoundException extends EventStoreException {

    /**
     * Initialize the exception with the given <code>message</code>.
     *
     * @param message a detailed message of the cause of the exception
     */
    public EventStreamNotFoundException(String message) {
        super(message);
    }

    /**
     * Initialize the exception with the given <code>message</code> and <code>cause</code>
     *
     * @param message a detailed message of the cause of the exception
     * @param cause   the original cause of this exception
     */
    public EventStreamNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

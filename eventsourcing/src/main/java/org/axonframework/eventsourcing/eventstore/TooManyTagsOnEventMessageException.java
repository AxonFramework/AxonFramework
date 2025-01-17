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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;

import java.util.Set;

/**
 * Exception indicating that an Event could not be appended to the event store because it contains more tags than the
 * storage engine can support.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class TooManyTagsOnEventMessageException extends IllegalArgumentException {

    private final EventMessage<?> eventMessage;
    private final Set<Tag> tags;

    /**
     * Initialize the exception with given explanatory {@code message} for logging, referencing the given
     * {@code eventMessage} and {@code tags} for debug purposes.
     *
     * @param message      The message describing the exception.
     * @param eventMessage The violating message.
     * @param tags         The tags assigned to the message.
     */
    public TooManyTagsOnEventMessageException(String message, EventMessage<?> eventMessage, Set<Tag> tags) {
        super(message);
        this.eventMessage = eventMessage;
        this.tags = tags;
    }

    /**
     * Returns the message that was rejected by the storage engine.
     *
     * @return the message that was rejected by the storage engine.
     */
    public EventMessage<?> eventMessage() {
        return eventMessage;
    }

    /**
     * Returns the tags assigned to the message.
     *
     * @return the tags assigned to the message.
     */
    public Set<Tag> tags() {
        return tags;
    }
}

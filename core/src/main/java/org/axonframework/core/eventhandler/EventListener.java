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

package org.axonframework.core.eventhandler;

import org.axonframework.core.Event;

/**
 * Interface to be implemented by classes that can handle events.
 *
 * @author Allard Buijze
 * @see org.axonframework.core.eventhandler.EventBus
 * @see org.axonframework.core.DomainEvent
 * @see org.axonframework.core.eventhandler.annotation.EventHandler
 * @since 0.1
 */
public interface EventListener {

    /**
     * TODO (issue #21): Remove this method. Implementations should decide how they do this themselves. Indicates
     * whether this event listener can handle events of the given type. This method is used as an early detection during
     * the dispatching process.
     *
     * @param eventType the type of event
     * @return true if this event listener can handle the event, false otherwise
     */
    boolean canHandle(Class<? extends Event> eventType);

    /**
     * Process the given event. There are no guarantees that this method is not called with types for which {@link
     * #canHandle(Class)} returned false. In such case, this method should return normally (typically without doing
     * anything).
     *
     * @param event the event to handle
     */
    void handle(Event event);

    /**
     * TODO (issue #21): Remove this method. It has to do with Asynchronous Event Handling, and should move to a
     * specialized class The Event sequencing policy applicable to this event listener. This policy defines which Events
     * must be processed sequentially, and which may run in parallel.
     *
     * @return the Event sequencing policy applicable to this event listener
     */
    EventSequencingPolicy getEventSequencingPolicy();
}

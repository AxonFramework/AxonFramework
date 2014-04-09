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

package org.axonframework.domain;

/**
 * Callback that allows components to be notified of an event being registered with an Aggregate. It also allows these
 * components to alter the generated event, before it is passed to the aggregate's Event Handler (if it is an Event
 * Sourced Aggregate).
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface EventRegistrationCallback {

    /**
     * Invoked when an Aggregate registers an Event for publication. The simplest implementation will simply return
     * the given <code>event</code>.
     *
     * @param event The event registered for publication
     * @param <T>   The type of payload
     * @return the message to actually publish. May <em>not</em> be <code>null</code>.
     */
    <T> DomainEventMessage<T> onRegisteredEvent(DomainEventMessage<T> event);
}

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

package org.axonframework.eventhandling;

import org.axonframework.domain.Event;

/**
 * Specification of the mechanism on which the Event Listeners can subscribe for events and event publishers can publish
 * their events. The event bus dispatches event to all subscribed listeners.
 * <p/>
 * Implementations may or may not dispatch the events to event listeners in the dispatching thread.
 *
 * @author Allard Buijze
 * @see EventListener
 * @see SimpleEventBus
 * @since 0.1
 */
public interface EventBus {

    /**
     * Publish an event on this bus. It is dispatched to all subscribed event listeners.
     *
     * @param event the event to publish
     */
    void publish(Event event);

    /**
     * Subscribe the given <code>eventListener</code> to this bus. When subscribed, it will receive all events published
     * to this bus.
     *
     * @param eventListener The event listener to subscribe
     */
    void subscribe(EventListener eventListener);

    /**
     * Unsubscribe the given <code>eventListener</code> to this bus. When unsubscribed, it will no longer receive events
     * published to this bus.
     *
     * @param eventListener The event listener to unsubscribe
     */
    void unsubscribe(EventListener eventListener);

}

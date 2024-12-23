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

package org.axonframework.eventhandling.gateway;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageDispatchInterceptorSupport;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Interface towards the Event Handling components of an application. This interface provides a friendlier API toward
 * the event bus. The EventGateway allows for components to easily publish events.
 *
 * @author Bert Laverman
 * @see DefaultEventGateway
 * @since 4.1
 */
public interface EventGateway extends MessageDispatchInterceptorSupport<EventMessage<?>> {

    /**
     * Publish a collection of events on this bus (one, or multiple). The events will be dispatched to all subscribed
     * listeners.
     * <p>
     * Implementations may treat the given {@code events} as a single batch and distribute the events as such to
     * all subscribed EventListeners.
     *
     * @param events The collection of events to publish
     */
    default void publish(Object... events) {
        publish(Arrays.asList(events));
    }

    /**
     * Publish a collection of events on this bus (one, or multiple). The events will be dispatched to all subscribed
     * listeners.
     * <p>
     * Implementations may treat the given {@code events} as a single batch and distribute the events as such to all
     * subscribed EventListeners.
     *
     * @param events The collection of events to publish
     */
    void publish(@Nonnull List<?> events);

    // todo: add something like publish(Object event, MetaData metaData, ProcessingContext context) to allow for additional meta data to be added
}

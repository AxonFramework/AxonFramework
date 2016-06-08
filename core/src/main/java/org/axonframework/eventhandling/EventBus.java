/*
 * Copyright (c) 2010-2016. Axon Framework
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

import org.axonframework.common.Registration;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MessageDispatchInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Specification of the mechanism on which the Event Listeners can subscribe for events and event publishers can publish
 * their events. The event bus dispatches events to all subscribed listeners.
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
     * Open an event stream containing all events since given tracking token. The returned stream is comprised of events
     * from aggregates as well as other application events. Pass a {@code trackingToken} of {@code null} to open a
     * stream containing all available events. Note that the returned stream is <em>infinite</em>, so beware of applying
     * terminal operations to the returned stream.
     * <p>
     * In case the event bus cannot open a stream for a given tracking token, for instance because the event bus does
     * not persist or cache events, the event bus will throw an {@link UnsupportedOperationException}.
     * <p>
     * In case any underlying storage has no more events the event store will block the thread that processes the event
     * stream until new events are added to the store or until the stream is closed.
     *
     * @param trackingToken object describing the global index of the last processed event or {@code null} to create a
     *                      stream of all events in the store
     * @return a stream of events since the given trackingToken
     * @throws UnsupportedOperationException in case this event bus does not support streaming from given token
     */
    TrackingEventStream streamEvents(TrackingToken trackingToken);

    /**
     * Publish a collection of events on this bus (one, or multiple). The events will be dispatched to all subscribed
     * listeners.
     * <p>
     * Implementations may treat the given {@code events} as a single batch and distribute the events as such to
     * all subscribed EventListeners.
     *
     * @param events The collection of events to publish
     */
    default void publish(EventMessage<?>... events) {
        publish(Arrays.asList(events));
    }

    /**
     * Publish a collection of events on this bus (one, or multiple). The events will be dispatched to all subscribed
     * listeners.
     * <p>
     * Implementations may treat the given {@code events} as a single batch and distribute the events as such to
     * all subscribed EventListeners.
     *
     * @param events The collection of events to publish
     */
    void publish(List<? extends EventMessage<?>> events);

    /**
     * Subscribe the given {@code eventProcessor} to this bus. When subscribed, it will receive all events
     * published to this bus.
     * <p>
     * If the given {@code eventProcessor} is already subscribed, nothing happens.
     *
     * @param eventProcessor The event processor to subscribe
     * @return a handle to unsubscribe the {@code eventProcessor}. When unsubscribed it will no longer receive
     * events.
     */
    Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor);

    /**
     * Register the given {@code interceptor} with this bus. When subscribed it will intercept any event messages
     * published on this bus.
     * <p>
     * If the given {@code interceptor} is already registered, nothing happens.
     *
     * @param dispatchInterceptor The event message dispatch interceptor to register
     * @return a handle to unregister the {@code dispatchInterceptor}. When unregistered it will no longer be given
     * event messages published on this bus.
     */
    Registration registerDispatchInterceptor(MessageDispatchInterceptor<EventMessage<?>> dispatchInterceptor);

}

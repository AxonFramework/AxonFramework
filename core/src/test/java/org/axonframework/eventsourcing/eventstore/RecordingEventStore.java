/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * EventBus implementation that does not perform any actions on subscriptions or published events, but records
 * them instead. This implementation is not a stand-in replacement for a mock, but might prove useful in many simple
 * cases.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class RecordingEventStore implements EventStore {

    private Collection<Consumer<List<? extends EventMessage<?>>>> subscriptions = new CopyOnWriteArraySet<>();
    private Collection<TrackingToken> readerTokens = new CopyOnWriteArrayList<>();
    private Collection<String> fetchedAggregateIds = new CopyOnWriteArrayList<>();
    private List<EventMessage<?>> publishedEvents = new ArrayList<>();
    private Collection<MessageDispatchInterceptor<EventMessage<?>>> dispatchInterceptors = new LinkedHashSet<>();

    @Override
    public TrackingEventStream streamEvents(TrackingToken trackingToken) {
        readerTokens.add(trackingToken);
        return new TrackingEventStream() {
            @Override
            public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
                return false;
            }

            @Override
            public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
                return new ArrayBlockingQueue<TrackedEventMessage<?>>(0).take();
            }

            @Override
            public void close() {

            }
        };
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier) {
        fetchedAggregateIds.add(aggregateIdentifier);
        return DomainEventStream.of();
    }

    @Override
    public void publish(List<? extends EventMessage<?>> events) {
        publishedEvents.addAll(events);
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
        subscriptions.add(eventProcessor);
        return () -> subscriptions.remove(eventProcessor);
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<EventMessage<?>> dispatchInterceptor) {
        this.dispatchInterceptors.add(dispatchInterceptor);
        return () -> this.dispatchInterceptors.remove(dispatchInterceptor);
    }

    /**
     * Clears all the events recorded by this Event Bus as well as all subscribed event processors.
     */
    public void reset() {
        publishedEvents.clear();
        subscriptions.clear();
        dispatchInterceptors.clear();
        readerTokens.clear();
        fetchedAggregateIds.clear();
    }

    /**
     * Indicates whether the given <code>eventProcessor</code> is subscribed to this Event Bus.
     *
     * @param eventProcessor The eventProcessor to verify the subscription for
     * @return <code>true</code> if the event processor is subscribed, otherwise <code>false</code>.
     */
    public boolean isSubscribed(EventProcessor eventProcessor) {
        return subscriptions.contains(eventProcessor);
    }

    /**
     * Returns a Collection of all subscribed EventProcessors on this Event Bus.
     *
     * @return a Collection of all subscribed EventProcessors
     */
    public Collection<Consumer<List<? extends EventMessage<?>>>> getSubscriptions() {
        return subscriptions;
    }

    /**
     * Returns a Collection of all tracking tokens of readers that opened an event stream on this Event Bus.
     *
     * @return a Collection of all tracking tokens of event readers
     */
    public Collection<TrackingToken> getReaderTokens() {
        return readerTokens;
    }

    /**
     * Returns a Collection of all identifiers of aggregates whose streams have been fetched.
     *
     * @return a Collection of all identifiers of fetched aggregate event streams
     */
    public Collection<String> getFetchedAggregateIds() {
        return fetchedAggregateIds;
    }

    /**
     * Returns a list with all events that have been published by this Event Bus.
     *
     * @return a list with all events that have been published
     */
    public List<? extends EventMessage<?>> getPublishedEvents() {
        return publishedEvents;
    }

    /**
     * Returns the number of events published by this Event Bus.
     *
     * @return the number of published events
     */
    public int getPublishedEventCount() {
        return publishedEvents.size();
    }

    /**
     * Indicates whether the given <code>messagePreprocessor</code> is subscribed to this Event Bus.
     *
     * @param dispatchInterceptor The messagePreprocessor to verify the subscription for
     * @return <code>true</code> if the messagePreprocessor is subscribed, otherwise <code>false</code>.
     */
    public boolean isSubscribed(MessageDispatchInterceptor<EventMessage<?>> dispatchInterceptor) {
        return this.dispatchInterceptors.contains(dispatchInterceptor);
    }

    /**
     * Returns a Collection of all subscribed MessagePreprocessors on this Event Bus.
     *
     * @return a Collection of all subscribed MessagePreprocessors
     */
    public Collection<MessageDispatchInterceptor<EventMessage<?>>> getDispatchInterceptors() {
        return dispatchInterceptors;
    }

}

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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Implementation of both {@link EventStore} and {@link EventBus} that delegates event store operations to an
 * {@link EventStore} and provides event bus functionality through an {@link EventBus}.
 * <p>
 * When events are {@link #publish(ProcessingContext, List) published}, they are first appended to the
 * {@code EventStore}, and then published to the {@code EventBus} for subscriber notification.
 * <p>
 * This component allows an {@code EventStore} to also function as an {@code EventBus}, enabling subscribers to receive
 * events as they are appended to the store.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class EventStoreEventBus implements EventStore, EventBus {

    private final EventStore eventStore;
    private final EventBus eventBus;

    /**
     * Constructs an {@code EventStoreEventBus} that delegates to the given {@code eventStore} for event store
     * operations and uses the given {@code eventBus} for subscription management.
     *
     * @param eventStore The {@link EventStore} to delegate event store operations to.
     * @param eventBus   The {@link EventBus} to use for subscription management.
     */
    public EventStoreEventBus(@Nonnull EventStore eventStore, @Nonnull EventBus eventBus) {
        this.eventStore = Objects.requireNonNull(eventStore, "EventStore may not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "EventBus may not be null");
    }

    @Override
    public EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext) {
        return eventStore.transaction(processingContext);
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        return eventStore.publish(context, events)
                         .thenApply(r -> {
                             eventBus.publish(context, events);
                             return null;
                         });
    }

    @Override
    public Registration subscribe(@Nonnull BiConsumer<List<? extends EventMessage>, ProcessingContext> eventsBatchConsumer) {
        return eventBus.subscribe(eventsBatchConsumer);
    }

    @Override
    public MessageStream<EventMessage> open(@Nonnull StreamingCondition condition, @Nullable ProcessingContext context) {
        return eventStore.open(condition, context);
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context) {
        return eventStore.firstToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context) {
        return eventStore.latestToken(context);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext context) {
        return eventStore.tokenAt(at, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventStore", eventStore);
        descriptor.describeProperty("eventBus", eventBus);
    }
}

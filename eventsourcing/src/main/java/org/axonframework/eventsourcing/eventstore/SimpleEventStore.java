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
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simple implementation of the {@link EventStore} and {@link StreamableEventSource}.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0.0
 */
public class SimpleEventStore implements EventStore, StreamableEventSource<EventMessage<?>> {

    private final EventStorageEngine eventStorageEngine;
    private final TagResolver tagResolver;

    private final ResourceKey<EventStoreTransaction> eventStoreTransactionKey;

    /**
     * Constructs a {@code SimpleEventStore} using the given {@code eventStorageEngine} to start
     * {@link #transaction(ProcessingContext) transactions} and {@link #open(StreamingCondition) open event streams}
     * with.
     *
     * @param eventStorageEngine The {@link EventStorageEngine} used to start
     *                           {@link #transaction(ProcessingContext) transactions} and
     *                           {@link #open(StreamingCondition) open event streams} with.
     * @param tagResolver        The {@link TagResolver} used to resolve tags during appending events in the
     *                           {@link EventStoreTransaction}.
     */
    public SimpleEventStore(@Nonnull EventStorageEngine eventStorageEngine,
                            @Nonnull TagResolver tagResolver) {
        this.eventStorageEngine = eventStorageEngine;
        this.tagResolver = tagResolver;

        this.eventStoreTransactionKey = ResourceKey.withLabel("eventStoreTransaction");
    }

    @Override
    public EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext) {
        return processingContext.computeResourceIfAbsent(
                eventStoreTransactionKey,
                () -> new DefaultEventStoreTransaction(eventStorageEngine, processingContext, tagResolver)
        );
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage<?>> events) {
        if (context == null) {
            AppendCondition none = AppendCondition.none();
            List<TaggedEventMessage<?>> taggedEvents = new ArrayList<>();
            for (EventMessage<?> event : events) {
                taggedEvents.add(new GenericTaggedEventMessage<>(event, tagResolver.resolve(event)));
            }
            return eventStorageEngine.appendEvents(none, taggedEvents)
                                     .thenApply(EventStorageEngine.AppendTransaction::commit)
                                     .thenApply(marker -> null);
        } else {
            // Return a completed future since we have an active context.
            // The user will wait within the context's lifecycle anyhow.
            appendToTransaction(context, events);
            return FutureUtils.emptyCompletedFuture();
        }
    }

    private void appendToTransaction(ProcessingContext context,
                                     List<EventMessage<?>> events) {
        EventStoreTransaction transaction = transaction(context);
        for (EventMessage<?> event : events) {
            transaction.appendEvent(event);
        }
    }

    @Override
    public MessageStream<EventMessage<?>> open(@Nonnull StreamingCondition condition) {
        return eventStorageEngine.stream(condition);
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken() {
        return eventStorageEngine.firstToken();
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken() {
        return eventStorageEngine.latestToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return eventStorageEngine.tokenAt(at);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventStorageEngine", eventStorageEngine);
    }
}

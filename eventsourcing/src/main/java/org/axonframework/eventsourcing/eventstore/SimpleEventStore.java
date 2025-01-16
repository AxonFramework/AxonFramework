/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simple implementation of the {@link AsyncEventStore} and {@link StreamableEventSource} fixed for a <b>single</b>
 * {@code context}.
 * <p>
 * Invocations of this event store that provide a different {@code context} than is used during construction of this
 * event store will fail with an {@link IllegalArgumentException}.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0
 */
public class SimpleEventStore implements AsyncEventStore, StreamableEventSource<EventMessage<?>> {

    private final AsyncEventStorageEngine eventStorageEngine;
    private final String context;
    private final ResourceKey<EventStoreTransaction> eventStoreTransactionKey;

    /**
     * Constructs a {@link SimpleEventStore} using the given {@code eventStorageEngine} to start
     * {@link #transaction(ProcessingContext, String) transactions} and
     * {@link #open(String, StreamingCondition) open event streams} with.
     * <p>
     * Invocations of this event store are validated against the given {@code context}.
     *
     * @param eventStorageEngine The {@link AsyncEventStorageEngine} used to start
     *                           {@link #transaction(ProcessingContext, String) transactions} and
     *                           {@link #open(String, StreamingCondition) open event streams} with.
     * @param context            The (bounded) {@code context} this event store operates in.
     */
    public SimpleEventStore(@Nonnull AsyncEventStorageEngine eventStorageEngine,
                            @Nonnull String context) {
        this.eventStorageEngine = eventStorageEngine;
        this.context = context;
        this.eventStoreTransactionKey = ResourceKey.withLabel("eventStoreTransaction");
    }

    @Override
    public EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext,
                                             @Nonnull String context) {
        validate(context);
        return processingContext.computeResourceIfAbsent(
                eventStoreTransactionKey,
                () -> new DefaultEventStoreTransaction(eventStorageEngine, processingContext)
        );
    }

    @Override
    public void publish(@Nonnull ProcessingContext processingContext,
                        @Nonnull String context,
                        @Nonnull List<EventMessage<?>> events) {
        validate(context);
        EventStoreTransaction transaction = transaction(processingContext, context);
        for (EventMessage<?> event : events) {
            transaction.appendEvent(event);
        }
    }

    @Override
    public CompletableFuture<Void> publish(@Nonnull String context,
                                           @Nonnull List<EventMessage<?>> events) {
        throw new UnsupportedOperationException(
                """
                        Publishing events with the SimpleEventStore requires a ProcessingContext at all times.
                        Or, use an EventStoreTransaction as provided by the SimpleEventStore instead.
                        """
        );
    }

    @Override
    public MessageStream<EventMessage<?>> open(@Nonnull String context,
                                               @Nonnull StreamingCondition condition) {
        validate(context);
        return eventStorageEngine.stream(condition);
    }

    @Override
    public CompletableFuture<TrackingToken> headToken(@Nonnull String context) {
        validate(context);
        return eventStorageEngine.headToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken(@Nonnull String context) {
        validate(context);
        return eventStorageEngine.tailToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull String context,
                                                    @Nonnull Instant at) {
        validate(context);
        return eventStorageEngine.tokenAt(at);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventStorageEngine", eventStorageEngine);
        descriptor.describeProperty("context", context);
    }

    private void validate(String context) {
        if (this.context.equals(context)) {
            return;
        }
        // TODO - Discuss: Do we want a dedicated exception here?
        throw new IllegalArgumentException("Context '" + context + "' does not match context '" + this.context + "'");
    }
}

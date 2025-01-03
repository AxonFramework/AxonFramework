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
import org.axonframework.common.Context.ResourceKey;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * The default {@link EventStoreTransaction}.
 * <p>
 * While {@link #source(SourcingCondition, ProcessingContext) sourcing} it will map the {@link SourcingCondition} into
 * an {@link AppendCondition} for {@link #appendEvent(EventMessage) appending}, taking into account several sourcing
 * invocation might have occurred in the same {@link ProcessingContext}. During
 * {@link #appendEvent(EventMessage) appending} it will pass along a collection of {@link EventMessage events} to an
 * {@link AsyncEventStorageEngine} is part of the prepare commit phase of the {@link ProcessingContext}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DefaultEventStoreTransaction implements EventStoreTransaction {

    private final AsyncEventStorageEngine eventStorageEngine;
    private final ProcessingContext processingContext;
    private final List<Consumer<EventMessage<?>>> callbacks;

    private final ResourceKey<AppendCondition> appendConditionKey;
    private final ResourceKey<List<EventMessage<?>>> eventQueueKey;
    private final ResourceKey<ConsistencyMarker> appendPositionKey;

    /**
     * Constructs a {@code DefaultEventStoreTransaction} using the given {@code eventStorageEngine} to
     * {@link #appendEvent(EventMessage) append events} originating from the given {@code context}.
     *
     * @param eventStorageEngine The {@link AsyncEventStorageEngine} used to
     *                           {@link #appendEvent(EventMessage) append events} with.
     * @param processingContext  The {@link ProcessingContext} from which to
     *                           {@link #appendEvent(EventMessage) append events} and attach resources to.
     */
    public DefaultEventStoreTransaction(@Nonnull AsyncEventStorageEngine eventStorageEngine,
                                        @Nonnull ProcessingContext processingContext) {
        this.eventStorageEngine = eventStorageEngine;
        this.processingContext = processingContext;
        this.callbacks = new CopyOnWriteArrayList<>();

        this.appendConditionKey = ResourceKey.create("appendCondition");
        this.eventQueueKey = ResourceKey.create("eventQueue");
        this.appendPositionKey = ResourceKey.create("appendPosition");
    }

    @Override
    public MessageStream<? extends EventMessage<?>> source(@Nonnull SourcingCondition condition,
                                                           @Nonnull ProcessingContext context) {
        context.updateResource(
                appendConditionKey,
                appendCondition -> appendCondition == null
                        ? AppendCondition.withCriteria(condition.criteria())
                        : appendCondition.orCriteria(condition.criteria())
        );
        return eventStorageEngine.source(condition);
    }

    @Override
    public void appendEvent(@Nonnull EventMessage<?> eventMessage) {
        List<EventMessage<?>> eventQueue = processingContext.computeResourceIfAbsent(
                eventQueueKey,
                () -> {
                    attachAppendEventsStep();
                    return new CopyOnWriteArrayList<>();
                }
        );
        eventQueue.add(eventMessage);

        callbacks.forEach(callback -> callback.accept(eventMessage));
    }

    private void attachAppendEventsStep() {
        processingContext.onPrepareCommit(
                context -> {
                    AppendCondition appendCondition =
                            context.computeResourceIfAbsent(appendConditionKey, AppendCondition::none);
                    List<EventMessage<?>> eventQueue = context.getResource(eventQueueKey);

                    List<TaggedEventMessage<?>> taggedEvents = new ArrayList<>();
                    for (EventMessage<?> event : eventQueue) {
                        // TODO - Use a indexer function to define the indices to assign to each event
                        taggedEvents.add(new GenericTaggedEventMessage<>(
                                event, appendCondition.criteria().tags()
                        ));
                    }
                    return eventStorageEngine.appendEvents(appendCondition, taggedEvents)
                                             .thenAccept(tx -> {
                                                 processingContext.onCommit(c -> doCommit(context, tx));
                                                 processingContext.onError((ctx, p, e) -> tx.rollback());
                                             });
                }
        );
    }

    private CompletableFuture<ConsistencyMarker> doCommit(ProcessingContext commitContext,
                                             AsyncEventStorageEngine.AppendTransaction tx) {
        return tx.commit()
                 .whenComplete((position, exception) ->
                                       commitContext.putResource(appendPositionKey, position));
    }

    @Override
    public void onAppend(@Nonnull Consumer<EventMessage<?>> callback) {
        callbacks.add(callback);
    }

    @Override
    public ConsistencyMarker appendPosition(@Nonnull ProcessingContext context) {
        return getOrDefault(context.getResource(appendPositionKey), ConsistencyMarker.ORIGIN);
    }
}

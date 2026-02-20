/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.eventsourcing.eventstore.EventStorageEngine.AppendTransaction;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.Tag;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNullElse;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * The default {@link EventStoreTransaction}.
 * <p>
 * While {@link #source(SourcingCondition) sourcing} it will map the {@link SourcingCondition} into an
 * {@link AppendCondition} for {@link #appendEvent(EventMessage) appending}, taking into account several sourcing
 * invocation might have occurred in the same {@link ProcessingContext}. During
 * {@link #appendEvent(EventMessage) appending} it will pass along a collection of {@link EventMessage events} to an
 * {@link EventStorageEngine} is part of the prepare commit phase of the {@link ProcessingContext}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DefaultEventStoreTransaction implements EventStoreTransaction, MarkerExposingEventStoreTransaction {

    private final EventStorageEngine eventStorageEngine;
    private final ProcessingContext processingContext;
    private final Function<EventMessage, TaggedEventMessage<?>> eventTagger;

    private final List<Consumer<EventMessage>> callbacks;

    private final ResourceKey<AppendCondition> appendConditionKey;
    private final ResourceKey<List<TaggedEventMessage<?>>> eventQueueKey;
    private final ResourceKey<ConsistencyMarker> appendPositionKey;

    /**
     * Constructs a {@code DefaultEventStoreTransaction} using the given {@code eventStorageEngine} to
     * {@link #appendEvent(EventMessage) append events} originating from the given {@code context}.
     *
     * @param eventStorageEngine The {@link EventStorageEngine} used to {@link #appendEvent(EventMessage) append events}
     *                           with.
     * @param processingContext  The {@link ProcessingContext} from which to
     *                           {@link #appendEvent(EventMessage) append events} and attach resources to.
     * @param eventTagger        A function that will process each {@link EventMessage} to attach
     *                           {@link Tag Tags}, before it is added to the
     *                           transaction.
     */
    public DefaultEventStoreTransaction(@Nonnull EventStorageEngine eventStorageEngine,
                                        @Nonnull ProcessingContext processingContext,
                                        @Nonnull Function<EventMessage, TaggedEventMessage<?>> eventTagger) {
        this.eventStorageEngine = eventStorageEngine;
        this.processingContext = processingContext;
        this.eventTagger = eventTagger;
        this.callbacks = new CopyOnWriteArrayList<>();

        this.appendConditionKey = ResourceKey.withLabel("appendCondition");
        this.eventQueueKey = ResourceKey.withLabel("eventQueue");
        this.appendPositionKey = ResourceKey.withLabel("appendPosition");
    }

    @Override
    public MessageStream<? extends EventMessage> source(@Nonnull SourcingCondition condition) {
        return source(condition, null);
    }

    @Override  // TODO #4199 remove the cmRef parameter
    public MessageStream<? extends EventMessage> source(@Nonnull SourcingCondition condition, @Nullable AtomicReference<ConsistencyMarker> cmRef) {
        var appendCondition = processingContext.updateResource(
                appendConditionKey,
                ac -> ac == null
                        ? AppendCondition.withCriteria(condition.criteria())
                        : ac.orCriteria(condition.criteria())
        );
        MessageStream<EventMessage> source = eventStorageEngine.source(condition);
        if (appendCondition.consistencyMarker() != ConsistencyMarker.ORIGIN) {
            return source;
        }

        AtomicReference<ConsistencyMarker> markerReference = new AtomicReference<>(appendCondition.consistencyMarker());
        return source.onNext(entry -> {
                         ConsistencyMarker marker;
                         if ((marker = entry.getResource(ConsistencyMarker.RESOURCE_KEY)) != null) {
                             markerReference.set(marker);
                         }
                     })
                     .filter(entry -> entry.getResource(ConsistencyMarker.RESOURCE_KEY) == null)
                     .onComplete(() -> {
                         if (cmRef != null) {
                             cmRef.set(markerReference.get());
                         }
                         updateAppendPosition(markerReference);
                     });
    }

    /**
     * When reading is complete, we choose the lowest, non-ORIGIN appendPosition as our next appendPosition when reading
     * multiple times, the lowest consistency marker that we received from those streams (usually the first), is the
     * safest one to use.
     */
    private void updateAppendPosition(AtomicReference<ConsistencyMarker> markerReference) {
        processingContext.updateResource(
                appendPositionKey,
                current -> {
                    if (current == null || current == ConsistencyMarker.ORIGIN) {
                        // This is the first time we are sourcing events, as such will be the correct ConsistencyMarker.
                        return markerReference.get();
                    }
                    // We received a stream of events, while we already sourced before. The lowest of the two is the safest to use.
                    return current.lowerBound(markerReference.get());
                });
    }

    @Override
    public void appendEvent(@Nonnull EventMessage eventMessage) {
        List<TaggedEventMessage<?>> eventQueue = processingContext.computeResourceIfAbsent(
                eventQueueKey,
                () -> {
                    attachAppendEventsStep();
                    return new CopyOnWriteArrayList<>();
                }
        );
        eventQueue.add(eventTagger.apply(eventMessage));
        callbacks.forEach(callback -> callback.accept(eventMessage));
    }

    private void attachAppendEventsStep() {
        processingContext.onPrepareCommit(
                context -> {
                    // we need to update the condition with the marker that we may have updated during reading
                    AppendCondition appendCondition =
                            context.updateResource(appendConditionKey, current -> {
                                if (current == null || AppendCondition.none().equals(current)) {
                                    return AppendCondition.none();
                                }
                                return current.withMarker(getOrDefault(context.getResource(appendPositionKey),
                                                                       current.consistencyMarker()));
                            });
                    List<TaggedEventMessage<?>> eventQueue = context.getResource(eventQueueKey);

                    return eventStorageEngine.appendEvents(appendCondition, processingContext, eventQueue)
                                             .thenApply(DefaultEventStoreTransaction::castTransaction)
                                             .thenAccept(tx -> {
                                                 processingContext.onCommit(c -> tx.commit()
                                                     .thenAccept(v -> processingContext.onAfterCommit(c2 -> doAfterCommit(c2, tx, v)))
                                                 );
                                                 processingContext.onError((c, p, e) -> tx.rollback());
                                             });
                }
        );
    }

    private <R> CompletableFuture<ConsistencyMarker> doAfterCommit(ProcessingContext context,
                                                                   EventStorageEngine.AppendTransaction<R> tx,
                                                                   R commitResult) {
        return tx.afterCommit(commitResult)
                 .whenComplete((position, exception) -> {
                     if (position != null) {
                         context.updateResource(
                                 appendPositionKey,
                                 other -> position.upperBound(requireNonNullElse(other, ConsistencyMarker.ORIGIN)));
                     }
                 });
    }

    @Override
    public void onAppend(@Nonnull Consumer<EventMessage> callback) {
        callbacks.add(callback);
    }

    @Override
    public ConsistencyMarker appendPosition() {
        return getOrDefault(processingContext.getResource(appendPositionKey), ConsistencyMarker.ORIGIN);
    }

    @SuppressWarnings("unchecked")
    private static AppendTransaction<Object> castTransaction(AppendTransaction<?> at) {
        return (AppendTransaction<Object>) at;
    }
}

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
import org.axonframework.messaging.eventstreaming.EventCriteria;
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
public class DefaultEventStoreTransaction implements EventStoreTransaction {

    private final EventStorageEngine eventStorageEngine;
    private final ProcessingContext processingContext;
    private final Function<EventMessage, TaggedEventMessage<?>> eventTagger;
    private final EventCriteria explicitAppendCriteria;

    private final List<Consumer<EventMessage>> callbacks;

    private final ResourceKey<AppendCondition> appendConditionKey;
    private final ResourceKey<List<TaggedEventMessage<?>>> eventQueueKey;
    private final ResourceKey<ConsistencyMarker> appendPositionKey;

    /**
     * Constructs a {@code DefaultEventStoreTransaction} using the given {@code eventStorageEngine} to
     * {@link #appendEvent(EventMessage) append events} originating from the given {@code context}.
     * <p>
     * This constructor creates a transaction where the append criteria is derived from the source criteria
     * (default behavior for backward compatibility).
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
        this(eventStorageEngine, processingContext, eventTagger, null);
    }

    /**
     * Constructs a {@code DefaultEventStoreTransaction} using the given {@code eventStorageEngine} to
     * {@link #appendEvent(EventMessage) append events} originating from the given {@code context}, with an
     * optional explicit {@link EventCriteria} for consistency checking.
     * <p>
     * This constructor enables Dynamic Consistency Boundaries (DCB) where the criteria used for sourcing events
     * (loading state) can differ from the criteria used for consistency checking (appending events).
     * <p>
     * The provided {@code explicitAppendCriteria} only controls <em>which</em> events to check for conflicts.
     * The {@link ConsistencyMarker} (read position) is always determined by the events returned during
     * {@link #source(SourcingCondition) sourcing} — these two concerns are orthogonal.
     *
     * @param eventStorageEngine    The {@link EventStorageEngine} used to
     *                              {@link #appendEvent(EventMessage) append events} with.
     * @param processingContext     The {@link ProcessingContext} from which to
     *                              {@link #appendEvent(EventMessage) append events} and attach resources to.
     * @param eventTagger           A function that will process each {@link EventMessage} to attach
     *                              {@link Tag Tags}, before it is added to the transaction.
     * @param explicitAppendCriteria An optional explicit {@link EventCriteria} for consistency checking.
     *                              If {@code null}, the append criteria will be derived from the source criteria.
     * @see EventStore#transaction(EventCriteria, ProcessingContext)
     */
    public DefaultEventStoreTransaction(@Nonnull EventStorageEngine eventStorageEngine,
                                        @Nonnull ProcessingContext processingContext,
                                        @Nonnull Function<EventMessage, TaggedEventMessage<?>> eventTagger,
                                        @Nullable EventCriteria explicitAppendCriteria) {
        this.eventStorageEngine = eventStorageEngine;
        this.processingContext = processingContext;
        this.eventTagger = eventTagger;
        this.explicitAppendCriteria = explicitAppendCriteria;
        this.callbacks = new CopyOnWriteArrayList<>();

        this.appendConditionKey = ResourceKey.withLabel("appendCondition");
        this.eventQueueKey = ResourceKey.withLabel("eventQueue");
        this.appendPositionKey = ResourceKey.withLabel("appendPosition");
    }

    @Override
    public MessageStream<? extends EventMessage> source(@Nonnull SourcingCondition condition) {
        // CRITERIA handling:
        // The criteria and the ConsistencyMarker are orthogonal concerns:
        //  - Criteria defines WHICH events to check for conflicts
        //  - Marker defines FROM WHERE to start checking (always extracted from sourced events)
        AppendCondition appendCondition;
        if (explicitAppendCriteria != null) {
            // Use explicit append criteria (set once at transaction creation).
            // This enables DCB where source criteria differs from append criteria.
            appendCondition = processingContext.computeResourceIfAbsent(
                    appendConditionKey,
                    () -> AppendCondition.withCriteria(explicitAppendCriteria)
            );
        } else {
            // Default behavior: derive append criteria from source criteria
            appendCondition = processingContext.updateResource(
                    appendConditionKey,
                    ac -> ac == null
                            ? AppendCondition.withCriteria(condition.criteria())
                            : ac.orCriteria(condition.criteria())
            );
        }

        // MARKER handling: Always extract from sourced events
        // The marker represents the read position - it is always determined by what was actually read,
        // regardless of which criteria will be used for consistency checking
        MessageStream<EventMessage> source = eventStorageEngine.source(condition, processingContext);
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
                     .onComplete(() -> updateAppendPosition(markerReference));
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
                                    if (explicitAppendCriteria != null) {
                                        // Standalone append criteria without sourcing.
                                        // Marker will be resolved from the latest token.
                                        return AppendCondition.withCriteria(explicitAppendCriteria);
                                    }
                                    return AppendCondition.none();
                                }
                                return current.withMarker(getOrDefault(context.getResource(appendPositionKey),
                                                                       current.consistencyMarker()));
                            });
                    List<TaggedEventMessage<?>> eventQueue = context.getResource(eventQueueKey);

                    return resolveAppendCondition(appendCondition)
                            .thenCompose(condition ->
                                    eventStorageEngine.appendEvents(condition, processingContext, eventQueue)
                                                     .thenApply(DefaultEventStoreTransaction::castTransaction)
                                                     .thenAccept(tx -> {
                                                         processingContext.onCommit(c -> tx.commit(c)
                                                             .thenAccept(v -> processingContext.onAfterCommit(c2 -> doAfterCommit(c2, tx, v)))
                                                         );
                                                         processingContext.onError((c, p, e) -> tx.rollback(c));
                                                     })
                            );
                }
        );
    }

    /**
     * Resolves the {@link AppendCondition} by obtaining the {@link ConsistencyMarker} from the
     * {@link EventStorageEngine#latestToken(ProcessingContext) latest token} when no sourcing occurred.
     * <p>
     * When {@link #source(SourcingCondition)} is never called (e.g., standalone {@code @AppendCriteriaBuilder}),
     * the append condition starts with {@link ConsistencyMarker#ORIGIN}. Rather than checking against all events
     * from the beginning of time, this method resolves the latest position from the store, ensuring consistency
     * is only checked against events appended concurrently.
     */
    private CompletableFuture<AppendCondition> resolveAppendCondition(AppendCondition appendCondition) {
        if (appendCondition.consistencyMarker() == ConsistencyMarker.ORIGIN
                && processingContext.getResource(appendPositionKey) == null) {
            return eventStorageEngine.latestToken(processingContext)
                                     .thenApply(token -> appendCondition.withMarker(
                                             eventStorageEngine.consistencyMarker(token)
                                     ));
        }
        return CompletableFuture.completedFuture(appendCondition);
    }

    private <R> CompletableFuture<ConsistencyMarker> doAfterCommit(ProcessingContext context,
                                                                   EventStorageEngine.AppendTransaction<R> tx,
                                                                   R commitResult) {
        return tx.afterCommit(commitResult, context)
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

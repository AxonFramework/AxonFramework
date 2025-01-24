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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * The default {@link EventStoreTransaction}.
 * <p>
 * While {@link #source(SourcingCondition) sourcing} it will map the {@link SourcingCondition} into an
 * {@link AppendCondition} for {@link #appendEvent(EventMessage) appending}, taking into account several sourcing
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
    private final TagResolver tagResolver;
    private final List<Consumer<EventMessage<?>>> callbacks;

    private final ResourceKey<AppendCondition> appendConditionKey;
    private final ResourceKey<List<TaggedEventMessage<?>>> eventQueueKey;
    private final ResourceKey<ConsistencyMarker> appendPositionKey;

    /**
     * Constructs a {@code DefaultEventStoreTransaction} using the given {@code eventStorageEngine} to
     * {@link #appendEvent(EventMessage) append events} originating from the given {@code context}.
     *
     * @param eventStorageEngine The {@link AsyncEventStorageEngine} used to
     *                           {@link #appendEvent(EventMessage) append events} with.
     * @param processingContext  The {@link ProcessingContext} from which to
     *                           {@link #appendEvent(EventMessage) append events} and attach resources to.
     * @param tagResolver        The {@link TagResolver} used to resolve tags while
     *                           {@link #appendEvent(EventMessage) appending events}.
     */
    public DefaultEventStoreTransaction(@Nonnull AsyncEventStorageEngine eventStorageEngine,
                                        @Nonnull ProcessingContext processingContext,
                                        @Nonnull TagResolver tagResolver) {
        this.eventStorageEngine = eventStorageEngine;
        this.processingContext = processingContext;
        this.tagResolver = tagResolver;
        this.callbacks = new CopyOnWriteArrayList<>();

        this.appendConditionKey = ResourceKey.withLabel("appendCondition");
        this.eventQueueKey = ResourceKey.withLabel("eventQueue");
        this.appendPositionKey = ResourceKey.withLabel("appendPosition");
    }

    @Override
    public MessageStream<? extends EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        var appendCondition = processingContext.updateResource(
                appendConditionKey,
                ac -> ac == null
                        ? AppendCondition.withCriteria(condition.criteria())
                        : ac.orCriteria(condition.criteria())
        );
        MessageStream<EventMessage<?>> source = eventStorageEngine.source(condition);
        if (appendCondition.consistencyMarker() == ConsistencyMarker.ORIGIN) {
            AtomicReference<ConsistencyMarker> tracker = new AtomicReference<>(appendCondition.consistencyMarker());
            return source.onNext(e -> {
                ConsistencyMarker marker;
                if ((marker = e.getResource(ConsistencyMarker.RESOURCE_KEY)) != null) {
                    tracker.set(marker);
                }
            }).whenComplete(() -> {
                // when reading is complete, we choose the lowest, non-ORIGIN appendPosition as our next appendPosition
                // when reading multiple times, the lowest consistency marker that we received from those streams
                // (usually the first), is the safest one to use
                processingContext.updateResource(appendPositionKey,
                                                 current -> current == null
                                                         || current == ConsistencyMarker.ORIGIN
                                                         ? tracker.get()
                                                         : current.lowerBound(tracker.get()));
            });
        } else {
            return source;
        }
    }

    @Override
    public void appendEvent(@Nonnull EventMessage<?> eventMessage) {
        var eventQueue = processingContext.computeResourceIfAbsent(
                eventQueueKey,
                () -> {
                    attachAppendEventsStep();
                    return new CopyOnWriteArrayList<>();
                }
        );

        var tags = tagResolver.resolve(eventMessage);
        eventQueue.add(new GenericTaggedEventMessage<>(eventMessage, tags));

        callbacks.forEach(callback -> callback.accept(eventMessage));
    }

    private void attachAppendEventsStep() {
        processingContext.onPrepareCommit(
                context -> {
                    // we need to update the condition with the marker that we may have updated during reading
                    AppendCondition appendCondition =
                            context.updateResource(appendConditionKey, current -> {
                                if (current == null) {
                                    return AppendCondition.none();
                                }
                                return current.withMarker(getOrDefault(context.getResource(appendPositionKey),
                                                                       current.consistencyMarker()));
                            });
                    List<TaggedEventMessage<?>> eventQueue = context.getResource(eventQueueKey);

                    return eventStorageEngine.appendEvents(appendCondition, eventQueue)
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
                 .whenComplete((position, exception) -> {
                     if (position != null) {
                         commitContext.updateResource(appendPositionKey,
                                                      other -> position.upperBound(Objects.requireNonNullElse(
                                                              other,
                                                              ConsistencyMarker.ORIGIN)));
                     }
                 });
    }

    @Override
    public void onAppend(@Nonnull Consumer<EventMessage<?>> callback) {
        callbacks.add(callback);
    }

    @Override
    public ConsistencyMarker appendPosition() {
        return getOrDefault(processingContext.getResource(appendPositionKey), ConsistencyMarker.ORIGIN);
    }
}

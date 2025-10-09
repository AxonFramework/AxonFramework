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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.EmptyApplicationContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiFunction;

/**
 * Base class for the Event Bus. In case events are published while a ProcessingContext is active, the events are queued
 * and published during the commit phase of the ProcessingContext.
 * <p>
 * This implementation of the {@link EventBus} directly forwards all published events (in the callers' thread) to
 * subscribed event processors.
 *
 * @author Allard Buijze
 * @author René de Waele
 * @since 3.0
 */
public abstract class AbstractEventBus implements EventBus {

    private final Context.ResourceKey<List<EventMessage>> eventsKey = Context.ResourceKey.withLabel("EventBus_Events");
    private final EventSubscribers eventSubscribers = new EventSubscribers();
    private final UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Instantiate an {@link AbstractEventBus}.
     **/
    public AbstractEventBus() {
        this.unitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE); // todo: remove the constructor.
    }

    /**
     * Instantiate an {@link AbstractEventBus}.
     **/
    public AbstractEventBus(UnitOfWorkFactory unitOfWorkFactory) {
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    @Override
    public Registration subscribe(
            @Nonnull BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer
    ) {
        return eventSubscribers.subscribe(eventsBatchConsumer);
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context, @Nonnull List<EventMessage> events) {
        if (context == null) {
            return unitOfWorkFactory
                    .create()
                    .executeWithResult(ctx -> {
                        runInContext(ctx, events);
                        return FutureUtils.emptyCompletedFuture();
                    });
        }

        runInContext(context, events);
        return FutureUtils.emptyCompletedFuture();
    }

    private void runInContext(@Nonnull ProcessingContext context, @Nonnull List<EventMessage> events) {
        // Check if we're already in or past the commit phase - publishing is forbidden at this point
        if (context.isCommitted()) {
            throw new IllegalStateException(
                    "It is not allowed to publish events when the ProcessingContext has already been committed. "
                            + "Please start a new ProcessingContext before publishing events."
            );
        }

        // Register lifecycle handlers and create event queue on first publish in this context
        List<EventMessage> eventQueue = context.computeResourceIfAbsent(eventsKey, () -> {
            ArrayList<EventMessage> queue = new ArrayList<>();

            context.onPrepareCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    return processEventsInPhase(queuedEvents, ctx, this::prepareCommit);
                }
                return FutureUtils.emptyCompletedFuture();
            });

            context.onCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    return processEventsInPhase(queuedEvents, ctx, this::commit);
                }
                return FutureUtils.emptyCompletedFuture();
            });

            context.onAfterCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    return processEventsInPhase(queuedEvents, ctx, this::afterCommit);
                }
                return FutureUtils.emptyCompletedFuture();
            });

            // Clean up events resource on completion or error to free memory
            context.doFinally(ctx -> ctx.removeResource(eventsKey));

            return queue;
        });

        eventQueue.addAll(events);
    }

    /**
     * Process events during a specific phase, handling events published during the phase.
     *
     * @param queuedEvents The events queued for processing
     * @param context      The processing context
     * @param processor    The processor to invoke for the events
     * @return A {@link CompletableFuture} that completes when all event processing is done
     */
    private CompletableFuture<Void> processEventsInPhase(
            List<EventMessage> queuedEvents,
            ProcessingContext context,
            BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<Void>> processor
    ) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int processedItems = queuedEvents.size();

        // Create a copy to avoid concurrent modification during event publication
        futures.add(processor.apply(new ArrayList<>(queuedEvents), context));

        // Make sure events published during this phase are also processed
        while (processedItems < queuedEvents.size()) {
            List<EventMessage> newMessages = new ArrayList<>(
                    queuedEvents.subList(processedItems, queuedEvents.size())
            );
            processedItems = queuedEvents.size();
            futures.add(processor.apply(newMessages, context));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Process given {@code events} while the ProcessingContext is preparing for commit. The default implementation
     * passes the events to each registered event processor.
     *
     * @param events  Events to be published by this Event Bus
     * @param context The processing context, or {@code null} if no context is active
     */
    protected CompletableFuture<Void> prepareCommit(@Nonnull List<? extends EventMessage> events,
                                                    @Nullable ProcessingContext context) {
        return eventSubscribers.notifySubscribers(events, context);
    }

    /**
     * Process given {@code events} while the ProcessingContext is being committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected CompletableFuture<Void> commit(@Nonnull List<? extends EventMessage> events,
                                             @Nullable ProcessingContext context) {
        return FutureUtils.emptyCompletedFuture();
    }

    /**
     * Process given {@code events} after the ProcessingContext has been committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected CompletableFuture<Void> afterCommit(@Nonnull List<? extends EventMessage> events,
                                                  @Nullable ProcessingContext context) {
        return FutureUtils.emptyCompletedFuture();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventsKey", eventsKey);
        descriptor.describeProperty("eventSubscribers", eventSubscribers);
    }
}

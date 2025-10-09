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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
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

    /**
     * Instantiate an {@link AbstractEventBus}.
     **/
    public AbstractEventBus() {
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
            // No processing context, publish immediately - I exepect UnitOfWorkFactory will be useful to retrieve component!
            prepareCommit(events, null);
            commit(events, null);
            afterCommit(events, null);
            return FutureUtils.emptyCompletedFuture();
        }

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

            context.runOnPrepareCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    processEventsInPhase(queuedEvents, ctx, this::prepareCommit);
                }
            });

            context.runOnCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    processEventsInPhase(queuedEvents, ctx, this::commit);
                }
            });

            context.runOnAfterCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    processEventsInPhase(queuedEvents, ctx, this::afterCommit);
                }
            });

            // Clean up events resource on completion or error to free memory
            context.doFinally(ctx -> ctx.removeResource(eventsKey));

            return queue;
        });

        eventQueue.addAll(events);

        return FutureUtils.emptyCompletedFuture();
    }

    /**
     * Process events during a specific phase, handling events published during the phase.
     *
     * @param queuedEvents The events queued for processing
     * @param context      The processing context
     * @param processor    The processor to invoke for the events
     */
    private void processEventsInPhase(
            List<EventMessage> queuedEvents,
            ProcessingContext context,
            BiConsumer<List<? extends EventMessage>, ProcessingContext> processor
    ) {
        int processedItems = queuedEvents.size();
        // Create a copy to avoid concurrent modification during event publication
        processor.accept(new ArrayList<>(queuedEvents), context);

        // Make sure events published during this phase are also processed
        while (processedItems < queuedEvents.size()) {
            List<EventMessage> newMessages = new ArrayList<>(
                    queuedEvents.subList(processedItems, queuedEvents.size())
            );
            processedItems = queuedEvents.size();
            processor.accept(newMessages, context);
        }
    }

    /**
     * Process given {@code events} while the ProcessingContext is preparing for commit. The default implementation
     * passes the events to each registered event processor.
     *
     * @param events  Events to be published by this Event Bus
     * @param context The processing context, or {@code null} if no context is active
     */
    protected void prepareCommit(@Nonnull List<? extends EventMessage> events, @Nullable ProcessingContext context) {
        eventSubscribers.notifySubscribers(events, context).join();
    }

    /**
     * Process given {@code events} while the ProcessingContext is being committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void commit(@Nonnull List<? extends EventMessage> events, @Nullable ProcessingContext context) {
    }

    /**
     * Process given {@code events} after the ProcessingContext has been committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void afterCommit(@Nonnull List<? extends EventMessage> events, @Nullable ProcessingContext context) {
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventsKey", eventsKey);
        descriptor.describeProperty("eventSubscribers", eventSubscribers);
    }
}

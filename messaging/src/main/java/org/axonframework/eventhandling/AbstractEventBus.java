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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;

/**
 * Base class for the Event Bus. In case events are published while a ProcessingContext is active, the events are
 * queued and published during the commit phase of the ProcessingContext.
 * <p>
 * This implementation of the {@link EventBus} directly forwards all published events (in the callers' thread) to
 * subscribed event processors.
 *
 * @author Allard Buijze
 * @author René de Waele
 * @since 3.0
 */
public abstract class AbstractEventBus implements EventBus {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEventBus.class);

    private final Context.ResourceKey<List<EventMessage>> eventsKey = Context.ResourceKey.withLabel("EventBus_Events");
    private final Context.ResourceKey<Boolean> handlersRegistered = Context.ResourceKey.withLabel("EventBus_HandlersRegistered");
    private final Set<BiConsumer<List<? extends EventMessage>, ProcessingContext>> eventProcessors = new CopyOnWriteArraySet<>();

    /**
     * Instantiate an {@link AbstractEventBus} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate an {@link AbstractEventBus} instance
     */
    protected AbstractEventBus(Builder builder) {
        builder.validate();
    }

    @Override
    public Registration subscribe(
            @Nonnull BiConsumer<List<? extends EventMessage>, ProcessingContext> eventsBatchConsumer) {
        if (this.eventProcessors.add(eventsBatchConsumer)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Event Processor [{}] subscribed successfully", eventsBatchConsumer);
            }
        } else {
            logger.info("Event Processor [{}] not added. It was already subscribed", eventsBatchConsumer);
        }
        return () -> {
            if (eventProcessors.remove(eventsBatchConsumer)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Event Processor {} unsubscribed successfully", eventsBatchConsumer);
                }
                return true;
            } else {
                logger.info("Event Processor {} not removed. It was already unsubscribed", eventsBatchConsumer);
                return false;
            }
        };
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context, @Nonnull List<EventMessage> events) {
        if (context == null || !(context instanceof ProcessingLifecycle)) {
            // No processing context, publish immediately
            prepareCommit(events, null);
            commit(events);
            afterCommit(events);
            return FutureUtils.emptyCompletedFuture();
        }

        ProcessingLifecycle lifecycle = (ProcessingLifecycle) context;

        // Check if we've already registered handlers for this context
        Boolean registered = context.getResource(handlersRegistered);
        if (registered == null) {
            // First time publishing in this context, register lifecycle handlers
            context.putResource(handlersRegistered, Boolean.TRUE);
            context.putResource(eventsKey, new ArrayList<>());

            lifecycle.runOnPrepareCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    processEventsInPhase(queuedEvents, ctx, this::prepareCommit);
                }
            });

            lifecycle.runOnCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    commit(new ArrayList<>(queuedEvents));
                }
            });

            lifecycle.runOnAfterCommit(ctx -> {
                List<EventMessage> queuedEvents = ctx.getResource(eventsKey);
                if (queuedEvents != null && !queuedEvents.isEmpty()) {
                    afterCommit(new ArrayList<>(queuedEvents));
                }
            });
        }

        // Add events to the queue
        List<EventMessage> eventQueue = context.getResource(eventsKey);
        if (eventQueue != null) {
            eventQueue.addAll(events);
        }

        return FutureUtils.emptyCompletedFuture();
    }

    /**
     * Process events during a specific phase, handling events published during the phase.
     *
     * @param queuedEvents The events queued for processing
     * @param context      The processing context
     * @param processor    The processor to invoke for the events
     */
    private void processEventsInPhase(List<EventMessage> queuedEvents,
                                      ProcessingContext context,
                                      BiConsumer<List<? extends EventMessage>, ProcessingContext> processor) {
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
    protected void prepareCommit(List<? extends EventMessage> events, @Nullable ProcessingContext context) {
        eventProcessors.forEach(eventProcessor -> eventProcessor.accept(events, context));
    }

    /**
     * Process given {@code events} while the ProcessingContext is being committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void commit(List<? extends EventMessage> events) {
    }

    /**
     * Process given {@code events} after the ProcessingContext has been committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void afterCommit(List<? extends EventMessage> events) {
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventsKey", eventsKey);
        descriptor.describeProperty("eventProcessors", eventProcessors);
    }

    /**
     * Abstract Builder class to instantiate {@link AbstractEventBus} implementations.
     */
    public abstract static class Builder {

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            // Method kept for overriding
        }
    }
}

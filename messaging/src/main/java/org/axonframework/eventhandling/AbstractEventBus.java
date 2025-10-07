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
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.axonframework.messaging.unitofwork.LegacyUnitOfWork.Phase.*;

/**
 * Base class for the Event Bus. In case events are published while a Unit of Work is active the Unit of Work root
 * coordinates the timing and order of the publication.
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

    private final String eventsKey = this + "_EVENTS";
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
        if (CurrentUnitOfWork.isStarted()) {
            LegacyUnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
            Assert.state(!unitOfWork.phase().isAfter(PREPARE_COMMIT),
                         () -> "It is not allowed to publish events when the current Unit of Work has already been " +
                                 "committed. Please start a new Unit of Work before publishing events.");
            Assert.state(!unitOfWork.root().phase().isAfter(PREPARE_COMMIT),
                         () -> "It is not allowed to publish events when the root Unit of Work has already been " +
                                 "committed.");

            eventsQueue(unitOfWork).addAll(events);
        } else {
            prepareCommit(events);
            commit(events);
            afterCommit(events);
        }
        return FutureUtils.emptyCompletedFuture();
    }

    private List<EventMessage> eventsQueue(LegacyUnitOfWork<?> unitOfWork) {
        return unitOfWork.getOrComputeResource(eventsKey, r -> {
            List<EventMessage> eventQueue = new ArrayList<>();

            unitOfWork.onPrepareCommit(u -> {
                if (u.parent().isPresent() && !u.parent().get().phase().isAfter(PREPARE_COMMIT)) {
                    eventsQueue(u.parent().get()).addAll(eventQueue);
                } else {
                    int processedItems = eventQueue.size();
                    doWithEvents(this::prepareCommit, new ArrayList<>(eventQueue));
                    // Make sure events published during publication prepare commit phase are also published
                    while (processedItems < eventQueue.size()) {
                        List<? extends EventMessage> newMessages = new ArrayList<>(
                                eventQueue.subList(processedItems, eventQueue.size())
                        );
                        processedItems = eventQueue.size();
                        doWithEvents(this::prepareCommit, newMessages);
                    }
                }
            });
            unitOfWork.onCommit(u -> {
                if (u.parent().isPresent() && !u.root().phase().isAfter(COMMIT)) {
                    u.root().onCommit(w -> doWithEvents(this::commit, eventQueue));
                } else {
                    doWithEvents(this::commit, eventQueue);
                }
            });
            unitOfWork.afterCommit(u -> {
                if (u.parent().isPresent() && !u.root().phase().isAfter(AFTER_COMMIT)) {
                    u.root().afterCommit(w -> doWithEvents(this::afterCommit, eventQueue));
                } else {
                    doWithEvents(this::afterCommit, eventQueue);
                }
            });
            unitOfWork.onCleanup(u -> u.resources().remove(eventsKey));
            return eventQueue;
        });
    }

    /**
     * Returns a list of all the events staged for publication in this Unit of Work. Changing this list will not affect
     * the publication of events.
     *
     * @return a list of all the events staged for publication
     */
    protected List<EventMessage> queuedMessages() {
        if (!CurrentUnitOfWork.isStarted()) {
            return Collections.emptyList();
        }
        List<EventMessage> messages = new ArrayList<>();
        addStagedMessages(CurrentUnitOfWork.get(), messages);
        return messages;
    }

    private void addStagedMessages(LegacyUnitOfWork<?> unitOfWork, List<EventMessage> messages) {
        unitOfWork.parent().ifPresent(parent -> addStagedMessages(parent, messages));
        if (unitOfWork.isRolledBack()) {
            // staged messages are irrelevant if the UoW has been rolled back
            return;
        }
        List<EventMessage> stagedEvents = unitOfWork.getOrDefaultResource(eventsKey, Collections.emptyList());
        for (EventMessage stagedEvent : stagedEvents) {
            if (!messages.contains(stagedEvent)) {
                messages.add(stagedEvent);
            }
        }
    }


    private void doWithEvents(Consumer<List<? extends EventMessage>> eventsConsumer,
                              List<? extends EventMessage> events) {
        eventsConsumer.accept(events);
    }

    /**
     * Process given {@code events} while the Unit of Work root is preparing for commit. The default implementation
     * passes the events to each registered event processor.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void prepareCommit(List<? extends EventMessage> events) {
        eventProcessors.forEach(eventProcessor -> eventProcessor.accept(events, null));
    }

    /**
     * Process given {@code events} while the Unit of Work root is being committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void commit(List<? extends EventMessage> events) {
    }

    /**
     * Process given {@code events} after the Unit of Work has been committed. The default implementation does nothing.
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

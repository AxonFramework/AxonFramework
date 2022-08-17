/*
 * Copyright (c) 2010-2022. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.messaging.unitofwork.UnitOfWork.Phase.*;

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

    private final MessageMonitor<? super EventMessage<?>> messageMonitor;

    private final String eventsKey = this + "_EVENTS";
    private final Set<Consumer<List<? extends EventMessage<?>>>> eventProcessors = new CopyOnWriteArraySet<>();
    private final Set<MessageDispatchInterceptor<? super EventMessage<?>>> dispatchInterceptors = new CopyOnWriteArraySet<>();
    private final SpanFactory spanFactory;

    /**
     * Instantiate an {@link AbstractEventBus} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate an {@link AbstractEventBus} instance
     */
    protected AbstractEventBus(Builder builder) {
        builder.validate();
        this.messageMonitor = builder.messageMonitor;
        this.spanFactory = builder.spanFactory;
    }

    @Override
    public Registration subscribe(@Nonnull Consumer<List<? extends EventMessage<?>>> eventProcessor) {
        if (this.eventProcessors.add(eventProcessor)) {
            if (logger.isDebugEnabled()) {
                logger.debug("EventProcessor [{}] subscribed successfully", eventProcessor);
            }
        } else {
            logger.info("EventProcessor [{}] not added. It was already subscribed", eventProcessor);
        }
        return () -> {
            if (eventProcessors.remove(eventProcessor)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("EventListener {} unsubscribed successfully", eventProcessor);
                }
                return true;
            } else {
                logger.info("EventListener {} not removed. It was already unsubscribed", eventProcessor);
                return false;
            }
        };
    }

    /**
     * {@inheritDoc}
     * <p/>
     * In case a Unit of Work is active, the {@code preprocessor} is not invoked by this Event Bus until the Unit of
     * Work root is committed.
     *
     * @param dispatchInterceptor
     */
    @Override
    public Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @Override
    public void publish(@Nonnull List<? extends EventMessage<?>> events) {
        List<? extends EventMessage<?>> eventsWithContext = events
                .stream()
                .map(e -> spanFactory.createInternalSpan(getClass().getSimpleName() + ".publish", e)
                                     .runSupplier(() -> spanFactory.propagateContext(e)))
                .collect(Collectors.toList());
        List<MessageMonitor.MonitorCallback> ingested = eventsWithContext.stream()
                                                                         .map(messageMonitor::onMessageIngested)
                                                                         .collect(Collectors.toList());

        if (CurrentUnitOfWork.isStarted()) {
            UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
            Assert.state(!unitOfWork.phase().isAfter(PREPARE_COMMIT),
                         () -> "It is not allowed to publish events when the current Unit of Work has already been " +
                                 "committed. Please start a new Unit of Work before publishing events.");
            Assert.state(!unitOfWork.root().phase().isAfter(PREPARE_COMMIT),
                         () -> "It is not allowed to publish events when the root Unit of Work has already been " +
                                 "committed.");

            unitOfWork.afterCommit(u -> {
                ingested.forEach(MessageMonitor.MonitorCallback::reportSuccess);
            });
            unitOfWork.onRollback(uow -> {
                ingested.forEach(message -> message.reportFailure(uow.getExecutionResult().getExceptionResult()));
            });

            eventsQueue(unitOfWork).addAll(eventsWithContext);
        } else {
            try {
                prepareCommit(intercept(eventsWithContext));
                commit(eventsWithContext);
                afterCommit(eventsWithContext);
                ingested.forEach(MessageMonitor.MonitorCallback::reportSuccess);
            } catch (Exception e) {
                ingested.forEach(m -> m.reportFailure(e));
                throw e;
            }
        }
    }

    private List<EventMessage<?>> eventsQueue(UnitOfWork<?> unitOfWork) {
        return unitOfWork.getOrComputeResource(eventsKey, r -> {
            Span span = spanFactory.createInternalSpan(getClass().getSimpleName() + ".commit");
            List<EventMessage<?>> eventQueue = new ArrayList<>();

            unitOfWork.onPrepareCommit(u -> {
                span.start();
                if (u.parent().isPresent() && !u.parent().get().phase().isAfter(PREPARE_COMMIT)) {
                    eventsQueue(u.parent().get()).addAll(eventQueue);
                } else {
                    int processedItems = eventQueue.size();
                    doWithEvents(this::prepareCommit, intercept(eventQueue));
                    // Make sure events published during publication prepare commit phase are also published
                    while (processedItems < eventQueue.size()) {
                        List<? extends EventMessage<?>> newMessages =
                                intercept(eventQueue.subList(processedItems, eventQueue.size()));
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
            unitOfWork.onCleanup(u -> {
                u.resources().remove(eventsKey);
                span.end();
            });
            return eventQueue;
        });
    }

    /**
     * Returns a list of all the events staged for publication in this Unit of Work. Changing this list will not affect
     * the publication of events.
     *
     * @return a list of all the events staged for publication
     */
    protected List<EventMessage<?>> queuedMessages() {
        if (!CurrentUnitOfWork.isStarted()) {
            return Collections.emptyList();
        }
        List<EventMessage<?>> messages = new ArrayList<>();
        addStagedMessages(CurrentUnitOfWork.get(), messages);
        return messages;
    }

    private void addStagedMessages(UnitOfWork<?> unitOfWork, List<EventMessage<?>> messages) {
        unitOfWork.parent().ifPresent(parent -> addStagedMessages(parent, messages));
        if (unitOfWork.isRolledBack()) {
            // staged messages are irrelevant if the UoW has been rolled back
            return;
        }
        List<EventMessage<?>> stagedEvents = unitOfWork.getOrDefaultResource(eventsKey, Collections.emptyList());
        for (EventMessage<?> stagedEvent : stagedEvents) {
            if (!messages.contains(stagedEvent)) {
                messages.add(stagedEvent);
            }
        }
    }

    /**
     * Invokes all the dispatch interceptors.
     *
     * @param events The original events being published
     * @return The events to actually publish
     */
    protected List<? extends EventMessage<?>> intercept(List<? extends EventMessage<?>> events) {
        List<EventMessage<?>> preprocessedEvents = new ArrayList<>(events);
        for (MessageDispatchInterceptor<? super EventMessage<?>> preprocessor : dispatchInterceptors) {
            BiFunction<Integer, ? super EventMessage<?>, ? super EventMessage<?>> function =
                    preprocessor.handle(preprocessedEvents);
            for (int i = 0; i < preprocessedEvents.size(); i++) {
                preprocessedEvents.set(i, (EventMessage<?>) function.apply(i, preprocessedEvents.get(i)));
            }
        }
        return preprocessedEvents;
    }

    private void doWithEvents(Consumer<List<? extends EventMessage<?>>> eventsConsumer,
                              List<? extends EventMessage<?>> events) {
        eventsConsumer.accept(events);
    }

    /**
     * Process given {@code events} while the Unit of Work root is preparing for commit. The default implementation
     * signals the registered {@link MessageMonitor} that the given events are ingested and passes the events to each
     * registered event processor.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void prepareCommit(List<? extends EventMessage<?>> events) {
        eventProcessors.forEach(eventProcessor -> eventProcessor.accept(events));
    }

    /**
     * Process given {@code events} while the Unit of Work root is being committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void commit(List<? extends EventMessage<?>> events) {
    }

    /**
     * Process given {@code events} after the Unit of Work has been committed. The default implementation does nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void afterCommit(List<? extends EventMessage<?>> events) {
    }

    /**
     * Abstract Builder class to instantiate {@link AbstractEventBus} implementations.
     * <p>
     * The {@link MessageMonitor} is defaulted to an {@link NoOpMessageMonitor} and the {@link SpanFactory} defaults to
     * a {@link NoOpSpanFactory}..
     */
    public abstract static class Builder {

        private MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private SpanFactory spanFactory = NoOpSpanFactory.INSTANCE;

        /**
         * Sets the {@link MessageMonitor} to monitor ingested {@link EventMessage}s. Defaults to a
         * {@link NoOpMessageMonitor}.
         *
         * @param messageMonitor a {@link MessageMonitor} to monitor ingested {@link EventMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Sets the {@link SpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link NoOpSpanFactory} by default, which provides no tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull SpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

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

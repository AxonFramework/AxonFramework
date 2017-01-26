/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.axonframework.messaging.unitofwork.UnitOfWork.Phase.*;

/**
 * Base class for the Event Bus. In case events are published while a Unit of Work is active the Unit of Work root
 * coordinates the timing and order of the publication.
 * <p>
 * This implementation of the {@link EventBus} directly forwards all published events (in the callers' thread) to
 * subscribed event processors. Event processors are expected to implement asynchronous handling themselves or
 * alternatively open an event stream using {@link #openStream(TrackingToken)}.
 *
 * @author Allard Buijze
 * @author René de Waele
 * @since 3.0
 */
public abstract class AbstractEventBus implements EventBus {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEventBus.class);

    private final String eventsKey = this + "_EVENTS";
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;
    private final Set<Consumer<List<? extends EventMessage<?>>>> eventProcessors = new CopyOnWriteArraySet<>();
    private final Set<MessageDispatchInterceptor<EventMessage<?>>> dispatchInterceptors = new CopyOnWriteArraySet<>();

    /**
     * Initializes an event bus with a {@link NoOpMessageMonitor}.
     */
    public AbstractEventBus() {
        this(NoOpMessageMonitor.INSTANCE);
    }

    /**
     * Initializes an event bus. Uses the given {@code messageMonitor} to report ingested messages and report the
     * result of processing the message.
     *
     * @param messageMonitor The monitor used to monitor the ingested messages
     */
    public AbstractEventBus(MessageMonitor<? super EventMessage<?>> messageMonitor) {
        this.messageMonitor = messageMonitor;
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
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
     * In case a Unit of Work is active, the {@code preprocessor} is not invoked by this Event Bus until the Unit
     * of Work root is committed.
     *
     * @param dispatchInterceptor
     */
    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<EventMessage<?>> dispatchInterceptor) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    @Override
    public void publish(List<? extends EventMessage<?>> events) {
        if (CurrentUnitOfWork.isStarted()) {
            UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
            Assert.state(!unitOfWork.phase().isAfter(PREPARE_COMMIT),
                         () -> "It is not allowed to publish events when the current Unit of Work has already been " +
                                 "committed. " +
                                 "Please start a new Unit of Work before publishing events.");
            Assert.state(!unitOfWork.root().phase().isAfter(PREPARE_COMMIT),
                         () -> "It is not allowed to publish events when the root Unit of Work has already been " +
                                 "committed.");

            eventsQueue(unitOfWork).addAll(events);

        } else {
            prepareCommit(intercept(events));
            commit(events);
            afterCommit(events);
        }
    }

    private List<EventMessage<?>> eventsQueue(UnitOfWork<?> unitOfWork) {
        return unitOfWork.getOrComputeResource(eventsKey, r -> {

            List<EventMessage<?>> eventQueue = new ArrayList<>();

            unitOfWork.onPrepareCommit(u -> {
                if (u.parent().isPresent() && !u.parent().get().phase().isAfter(PREPARE_COMMIT)) {
                    eventsQueue(u.parent().get()).addAll(eventQueue);
                } else {
                    int processedItems = eventQueue.size();
                    doWithEvents(this::prepareCommit, intercept(eventQueue));
                    // make sure events published during publication prepare commit phase are also published
                    while (processedItems < eventQueue.size()) {
                        List<? extends EventMessage<?>> newMessages = intercept(eventQueue.subList(processedItems, eventQueue.size()));
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
     * Invokes all the dispatch interceptors.
     *
     * @param events The original events being published
     * @return The events to actually publish
     */
    protected List<? extends EventMessage<?>> intercept(List<? extends EventMessage<?>> events) {
        List<EventMessage<?>> preprocessedEvents = new ArrayList<>(events);
        for (MessageDispatchInterceptor<EventMessage<?>> preprocessor : dispatchInterceptors) {
            BiFunction<Integer, EventMessage<?>, EventMessage<?>> function = preprocessor.handle(preprocessedEvents);
            for (int i = 0; i < preprocessedEvents.size(); i++) {
                preprocessedEvents.set(i, function.apply(i, preprocessedEvents.get(i)));
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
        events.forEach(messageMonitor::onMessageIngested);
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
     * Process given {@code events} after the Unit of Work has been committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void afterCommit(List<? extends EventMessage<?>> events) {
    }
}

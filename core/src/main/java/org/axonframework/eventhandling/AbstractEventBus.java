package org.axonframework.eventhandling;

import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.axonframework.messaging.unitofwork.UnitOfWork.Phase.*;

/**
 * Base class for the Event Bus. In case events are published while a Unit of Work is active the Unit of Work root
 * coordinates the timing and order of the publication.
 * <p>
 * This implementation of the {@link EventBus} directly forwards all published events (in the callers' thread) to
 * subscribed event processors. Event processors are expected to implement asynchronous handling themselves or
 * alternatively open an event stream using {@link #streamEvents(TrackingToken)}.
 *
 * @author Allard Buijze
 * @author René de Waele
 * @since 3.0
 */
public abstract class AbstractEventBus implements EventBus {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEventBus.class);

    final String eventsKey = this + "_EVENTS";
    private final Set<Consumer<List<? extends EventMessage<?>>>> eventProcessors = new CopyOnWriteArraySet<>();
    private final Set<MessageDispatchInterceptor<EventMessage<?>>> dispatchInterceptors = new CopyOnWriteArraySet<>();

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
     * In case a Unit of Work is active, the <code>preprocessor</code> is not invoked by this Event Bus until the Unit
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
                         "It is not allowed to publish events when the current Unit of Work has already been committed. " +
                                 "Please start a new Unit of Work before publishing events.");
            Assert.state(!unitOfWork.root().phase().isAfter(PREPARE_COMMIT),
                         "It is not allowed to publish events when the root Unit of Work has already been committed.");

            unitOfWork.getOrComputeResource(eventsKey, r -> {

                List<EventMessage<?>> eventQueue = new ArrayList<>();

                unitOfWork.onPrepareCommit(u -> {
                    if (u.parent().isPresent() && !u.root().phase().isAfter(PREPARE_COMMIT)) {
                        u.root().onPrepareCommit(w -> doWithEvents(this::prepareCommit, intercept(eventQueue)));
                    } else {
                        doWithEvents(this::prepareCommit, intercept(eventQueue));
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
                return eventQueue;

            }).addAll(events);

        } else {
            prepareCommit(intercept(events));
            commit(events);
            afterCommit(events);
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
        for (MessageDispatchInterceptor<EventMessage<?>> preprocessor : dispatchInterceptors) {
            Function<Integer, EventMessage<?>> function = preprocessor.handle(preprocessedEvents);
            for (int i = 0; i < preprocessedEvents.size(); i++) {
                preprocessedEvents.set(i, function.apply(i));
            }
        }
        return preprocessedEvents;
    }

    private void doWithEvents(Consumer<List<? extends EventMessage<?>>> eventsConsumer, List<? extends EventMessage<?>> events) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().resources().remove(eventsKey);
        }
        eventsConsumer.accept(events);
    }

    /**
     * Process given <code>events</code> while the Unit of Work root is preparing for commit. The default implementation
     * passes the events to each registered event processor.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void prepareCommit(List<? extends EventMessage<?>> events) {
        eventProcessors.forEach(eventProcessor -> eventProcessor.accept(events));
    }

    /**
     * Process given <code>events</code> while the Unit of Work root is being committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void commit(List<? extends EventMessage<?>> events) {
    }

    /**
     * Process given <code>events</code> after the Unit of Work has been committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void afterCommit(List<? extends EventMessage<?>> events) {
    }
}

package org.axonframework.eventhandling;

import org.axonframework.common.Assert;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base class for the Event Bus. In case events are published while a Unit of Work is active the Unit of Work root
 * coordinates the timing and order of the publication.
 *
 * @author Allard Buijze
 * @author René de Waele
 * @since 3.0
 */
public abstract class AbstractEventBus implements EventBus {

    final String eventsKey = this + "_EVENTS";

    @Override
    public void publish(List<EventMessage<?>> events) {
        if (CurrentUnitOfWork.isStarted()) {
            UnitOfWork unitOfWork = CurrentUnitOfWork.get();
            Assert.state(!unitOfWork.phase().isAfter(UnitOfWork.Phase.PREPARE_COMMIT),
                    "It is not allowed to publish events when the current Unit of Work has already been committed. " +
                    "Please start a new Unit of Work before publishing events.");

            unitOfWork.getOrComputeResource(eventsKey, r -> {

                List<EventMessage<?>> eventQueue = new ArrayList<>();
                unitOfWork.onPrepareCommit(u -> {
                    if (u.parent().isPresent() && !u.root().phase().isAfter(UnitOfWork.Phase.PREPARE_COMMIT)) {
                        u.root().onPrepareCommit(w -> doWithEvents(this::prepareCommit, eventQueue));
                    } else {
                        doWithEvents(this::prepareCommit, eventQueue);
                    }
                });
                unitOfWork.onCommit(u -> {
                    if (u.parent().isPresent() && !u.root().phase().isAfter(UnitOfWork.Phase.COMMIT)) {
                        u.root().onCommit(w -> doWithEvents(this::commit, eventQueue));
                    } else {
                        doWithEvents(this::commit, eventQueue);
                    }
                });
                unitOfWork.afterCommit(u -> {
                    if (u.parent().isPresent() && !u.root().phase().isAfter(UnitOfWork.Phase.AFTER_COMMIT)) {
                        u.root().afterCommit(w -> doWithEvents(this::afterCommit, eventQueue));
                    } else {
                        doWithEvents(this::afterCommit, eventQueue);
                    }
                });
                return eventQueue;

            }).addAll(events);

        } else {
            prepareCommit(events);
            commit(events);
            afterCommit(events);
        }
    }

    private void doWithEvents(Consumer<List<EventMessage<?>>> eventsConsumer, List<EventMessage<?>> events) {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().resources().remove(eventsKey);
        }
        eventsConsumer.accept(new ArrayList<>(events));
    }

    /**
     * Process given <code>events</code> while the Unit of Work root is preparing for commit. The default
     * implementation does nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void prepareCommit(List<EventMessage<?>> events) {
    }

    /**
     * Process given <code>events</code> while the Unit of Work root is being committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void commit(List<EventMessage<?>> events) {
    }

    /**
     * Process given <code>events</code> after the Unit of Work has been committed. The default implementation does
     * nothing.
     *
     * @param events Events to be published by this Event Bus
     */
    protected void afterCommit(List<EventMessage<?>> events) {
    }
}

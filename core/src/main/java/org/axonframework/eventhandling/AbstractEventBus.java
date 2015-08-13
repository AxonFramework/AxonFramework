package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for the Event Bus. In case events are published while a Unit of Work is active the Unit of Work
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

            unitOfWork.getOrComputeResource(eventsKey, r -> {
                List<EventMessage<?>> eventQueue = new ArrayList<>();

                unitOfWork.onPrepareCommit(u -> {
                    if (u.parent().isPresent()) {
                        u.root().onPrepareCommit(w -> prepareCommit(new ArrayList<>(eventQueue)));
                    } else {
                        prepareCommit(new ArrayList<>(eventQueue));
                    }
                });
                unitOfWork.onCommit(u -> {
                    if (u.parent().isPresent()) {
                        u.root().onCommit(w -> commit(new ArrayList<>(eventQueue)));
                    } else {
                        commit(new ArrayList<>(eventQueue));
                    }
                });
                unitOfWork.afterCommit(u -> {
                    if (u.parent().isPresent()) {
                        u.root().afterCommit(w -> afterCommit(new ArrayList<>(eventQueue)));
                    } else {
                        afterCommit(new ArrayList<>(eventQueue));
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
    
    protected void prepareCommit(List<EventMessage<?>> events) {
    }

    protected void commit(List<EventMessage<?>> events) {
    }

    protected void afterCommit(List<EventMessage<?>> events) {
    }
}

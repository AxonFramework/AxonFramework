package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.unitofwork.CurrentUnitOfWork;

import java.util.List;

/**
 * TODO: Add documentation
 *
 * @author Allard Buijze
 */
public abstract class AbstractEventBus implements EventBus {

    @Override
    public void publish(List<EventMessage<?>> events) {
        if (CurrentUnitOfWork.isStarted()) {
            // TODO: Prevent barging of events. Make sure root UoW coordinates order of publication
            CurrentUnitOfWork.get().onPrepareCommit(u -> {
                if (u.parent().isPresent()) {
                    u.root().onPrepareCommit(w -> prepareCommit(events));
                } else {
                    prepareCommit(events);
                }
            });

            CurrentUnitOfWork.get().afterCommit(u -> afterCommit(events));
            CurrentUnitOfWork.get().onCommit(u -> commit(events));
        } else {
            prepareCommit(events);
            commit(events);
            afterCommit(events);
        }
    }

    protected void prepareCommit(List<EventMessage<?>> events) {
    }

    protected void afterCommit(List<EventMessage<?>> events) {
    }

    protected void commit(List<EventMessage<?>> events) {
    }
}

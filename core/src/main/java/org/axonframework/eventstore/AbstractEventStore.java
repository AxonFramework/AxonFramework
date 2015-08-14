package org.axonframework.eventstore;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.Cluster;
import org.axonframework.eventhandling.EventBus;

import java.util.List;

/**
 * Abstract implementation of EventStore interface that also functions as EventBus. This implementation delegates
 * event publication to another EventBus instance, but stores events before they get published.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class AbstractEventStore implements EventStore, EventBus {

    private final EventBus eventBus;

    public AbstractEventStore(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void publish(List<EventMessage<?>> events) {
        //TODO: store events once event store supports storing of EventMessage over DomainEventMessage
        eventBus.publish(events);
    }

    @Override
    public void subscribe(Cluster cluster) {
        eventBus.subscribe(cluster);
    }

    @Override
    public void unsubscribe(Cluster cluster) {
        eventBus.unsubscribe(cluster);
    }
}

package org.axonframework.eventstore.mongo;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Rene de Waele
 */
class StubAggregateRoot extends AbstractAnnotatedAggregateRoot {

    private final String identifier;
    private transient List<DomainEventMessage<?>> registeredEvents;

    StubAggregateRoot() {
        this.identifier = UUID.randomUUID().toString();
    }

    StubAggregateRoot(String identifier) {
        this.identifier = identifier;
    }

    public void changeState() {
        apply(new StubStateChangedEvent());
    }

    @Override
    protected <T> void registerEventMessage(EventMessage<T> message) {
        super.registerEventMessage(message);
        getRegisteredEvents().add((DomainEventMessage<?>) message);
    }

    public List<DomainEventMessage<?>> getRegisteredEvents() {
        if (registeredEvents == null) {
            registeredEvents = new ArrayList<>();
        }
        return registeredEvents;
    }

    public int getRegisteredEventCount() {
        return registeredEvents == null ? 0 : registeredEvents.size();
    }

    public void reset() {
        registeredEvents.clear();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @EventSourcingHandler
    public void handleStateChange(StubStateChangedEvent event) {
    }

    public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
        return new GenericDomainEventMessage<>(
                getIdentifier(), getVersion(), new StubStateChangedEvent(), null);
    }
}

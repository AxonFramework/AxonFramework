package org.axonframework.eventstore.mongo;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.annotation.AggregateIdentifier;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * @author Rene de Waele
 */
class StubAggregateRoot {

    @AggregateIdentifier
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

    @EventHandler
    protected <T> void registerEventMessage(EventMessage<T> message) {
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

    @EventSourcingHandler
    public void handleStateChange(StubStateChangedEvent event) {
    }

    public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
        return new GenericDomainEventMessage<>(
                type, identifier, getRegisteredEventCount() - 1, new StubStateChangedEvent(), null);
    }

    public String getIdentifier() {
        return identifier;
    }
}

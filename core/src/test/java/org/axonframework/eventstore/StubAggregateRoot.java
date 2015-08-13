package org.axonframework.eventstore;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Rene de Waele
 */
public class StubAggregateRoot extends AbstractAnnotatedAggregateRoot {

    private static final long serialVersionUID = -3656612830058057848L;
    private transient List<DomainEventMessage<?>> registeredEvents;
    private final Object identifier;

    public StubAggregateRoot() {
        this(UUID.randomUUID());
    }

    public StubAggregateRoot(Object identifier) {
        this.identifier = identifier;
    }

    public void changeState() {
        apply(new StubStateChangedEvent());
    }

    public void reset() {
        registeredEvents.clear();
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

    @Override
    public String getIdentifier() {
        return identifier.toString();
    }

    @EventSourcingHandler
    public void handleStateChange(StubStateChangedEvent event) {
    }

    public DomainEventMessage<StubStateChangedEvent> createSnapshotEvent() {
        return new GenericDomainEventMessage<>(getIdentifier(), getVersion(),
                new StubStateChangedEvent(),
                MetaData.emptyInstance()
        );
    }
}

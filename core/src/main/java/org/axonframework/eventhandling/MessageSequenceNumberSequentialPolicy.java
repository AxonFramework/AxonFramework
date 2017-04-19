package org.axonframework.eventhandling;

import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventsourcing.DomainEventMessage;

import java.util.Objects;
import java.util.UUID;

/**
 * A policy which guarantees a unique Identifier.
 *
 * @author Christophe Bouhier
 */
public class MessageSequenceNumberSequentialPolicy implements SequencingPolicy<EventMessage> {

    @Override
    public Object getSequenceIdentifierFor(EventMessage event) {
        if(event instanceof  DomainEventMessage<?>){
            return ((DomainEventMessage) event).getSequenceNumber();
        }
        return null;
    }
}
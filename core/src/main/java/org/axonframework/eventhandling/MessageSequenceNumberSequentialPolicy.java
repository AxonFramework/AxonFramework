package org.axonframework.eventhandling;

import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventsourcing.DomainEventMessage;

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
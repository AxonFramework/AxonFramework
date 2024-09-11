package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * An {@code Index} refers to fields and their values within which an
 * {@link org.axonframework.eventhandling.EventMessage} has been published that represent an index of the event.
 * <p>
 * Such a {@code Index} is typically used by the {@link EventCriteria} as a filter when
 * {@link EventStoreTransaction#source(SourcingCondition, ProcessingContext) sourcing},
 * {@link StreamableEventSource#open(String, StreamingCondition) streaming} or
 * {@link EventStoreTransaction#appendEvent(EventMessage) appending} events.
 *
 * @param key   The key of this {@link Index}.
 * @param value The value of this {@link Index}.
 * @author Allard Buijze
 * @author Michal Negacz
 * @author Milan Savić
 * @author Marco Amann
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record Index(String key, String value) {

}

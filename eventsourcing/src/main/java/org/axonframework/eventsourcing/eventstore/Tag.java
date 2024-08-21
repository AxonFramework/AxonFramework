package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * A {@code Tag} refers to fields and their values within which an {@link org.axonframework.eventhandling.EventMessage}
 * has been published.
 * <p>
 * Such a {@code Tag} is typically used by the {@link EventCriteria} as a filter when
 * {@link EventSourcingTransaction#source(SourcingCondition, ProcessingContext) sourcing},
 * {@link AsyncEventStore#stream(StreamingCondition) streaming} or
 * {@link EventSourcingTransaction#appendEvent(EventMessage) appending} events.
 *
 * @param key   The key of this {@link Tag}.
 * @param value The value of this {@link Tag}.
 * @author Marco Amann
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 5.0.0
 */
public record Tag(String key, String value) {

}

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

/**
 * Interface describing criteria to be taken into account when
 * {@link EventSourcingTransaction#source(SourcingCondition, ProcessingContext) sourcing},
 * {@link AsyncEventStore#stream(StreamingCondition) streaming} or
 * {@link EventSourcingTransaction#appendEvent(EventMessage) appending} events.
 * <p>
 * During sourcing or streaming, the {@link #types()} and {@link #tags()} are used as a filter. While appending events,
 * the {@code #types()} and {@code #tags()} are used to validate the consistency boundary of the event(s) to append. The
 * latter happens in tandem with the {@link AppendCondition#consistencyMarker()}.
 *
 * @author Marco Amann
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface EventCriteria {

    /**
     * Construct a {@link EventCriteria} that contains no criteria at all.
     * <p>
     * Use this instance when all events are of interest during
     * {@link AsyncEventStore#stream(StreamingCondition) streaming} or when there are no consistency boundaries to
     * validate during {@link EventSourcingTransaction#appendEvent(EventMessage) appending}. Note that this
     * {@link EventCriteria} does not make sense for
     * {@link EventSourcingTransaction#source(SourcingCondition, ProcessingContext) sourcing}, as it is <b>not</b>
     * recommended to source the entire event store.
     *
     * @return An {@link EventCriteria} that contains no criteria at all.
     */
    static EventCriteria noCriteria() {
        return NoEventCriteria.INSTANCE;
    }

    /**
     * Construct a simple {@link EventCriteria} based on the given {@code name} as the identifier name and {@code value}
     * as the identifier's value.
     *
     * @param name  The name of the identifier.
     * @param value The value of the identifier.
     * @return A simple {@link EventCriteria} that can filter on the {@code name} and {@code value} combination.
     */
    static EventCriteria hasIdentifier(String name, String value) {
        return new HasIdentifier(name, value);
    }

    /**
     * A {@link Set} of {@link String} containing all the types of events applicable for sourcing, streaming, or
     * appending events.
     *
     * @return The {@link Set} of {@link String} containing all the types of events applicable for sourcing, streaming,
     * or appending events.
     */
    Set<String> types();

    /**
     * A {@link Set} of {@link Tag Tags} applicable for sourcing, streaming, or appending events. A {@code Tag} can, for
     * example, refer to a model's (aggregate) identifier name and value.
     *
     * @return The {@link Set} of {@link Tag tags} applicable for sourcing, streaming, or appending events.
     */
    Set<Tag> tags();

    /**
     * Combines {@code this} {@link EventCriteria} with {@code that EventCriteria}
     *
     * @param that The {@link EventCriteria} to combine with {@code this}.
     * @return A combined {@link EventCriteria}, consisting out of {@code this} and the given {@code that}.
     */
    default EventCriteria combine(EventCriteria that) {
        return new CombinedEventCriteria(this, that);
    }
}

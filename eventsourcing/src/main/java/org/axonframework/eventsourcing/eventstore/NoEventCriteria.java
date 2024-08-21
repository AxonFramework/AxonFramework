package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

/**
 * A no-op implementation of the {@link EventCriteria}.
 * <p>
 * Use this instance when all events are of interest during {@link AsyncEventStore#stream(StreamingCondition) streaming}
 * or when there are no consistency boundaries to validate during
 * {@link EventSourcingTransaction#appendEvent(EventMessage) appending}. Note that {@code EventCriteria} criteria does
 * not make sense for {@link EventSourcingTransaction#source(SourcingCondition, ProcessingContext) sourcing}, as it is
 * <b>not</b> recommended to source the entire event store.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class NoEventCriteria implements EventCriteria {

    /**
     * Default instance of the {@link NoEventCriteria}.
     */
    static final NoEventCriteria INSTANCE = new NoEventCriteria();

    @Override
    public Set<String> types() {
        return Set.of();
    }

    @Override
    public Set<Tag> tags() {
        return Set.of();
    }
}

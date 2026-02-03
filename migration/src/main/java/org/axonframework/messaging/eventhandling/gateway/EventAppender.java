package org.axonframework.messaging.eventhandling.gateway;

import org.axonframework.modelling.command.AggregateLifecycle;

import java.util.Arrays;
import java.util.List;

/**
 * Component that appends events to a  in the context of a {@link ProcessingContext}. This makes the
 * {@code EventAppender} the <b>preferred</b> way to append events from within another message handling method.
 * <p>
 * The events will be appended in the context this appender was created for. You can construct one through the
 * {@link #forContext(ProcessingContext)}.
 * <p>
 * When using annotation-based {@link MessageHandler @MessageHandler-methods} and you have declared an argument of type
 * {@link EventAppender}, the appender will automatically be injected by the
 * {@link EventAppenderParameterResolverFactory}.
 * <p>
 * As this component is {@link ProcessingContext}-scoped, it is not retrievable from the {@link Configuration}.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 4.13.0
 */
public interface EventAppender {

    /**
     * Append a collection of events to the event store in the current
     * {@link org.axonframework.messaging.unitofwork.UnitOfWork}. The events will be published when the context
     * commits.
     *
     * @param events The collection of events to publish.
     */
    default void append(Object... events) {
        append(Arrays.asList(events));
    }

    /**
     * Append a collection of events to the event store in the current
     * {@link org.axonframework.messaging.unitofwork.UnitOfWork}. The events will be published when the context
     * commits.
     *
     * @param events The collection of events to publish.
     */
    static void append(List<?> events) {
        for (Object event : events) {
            AggregateLifecycle.apply(event);
        }
    }
}

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventMessage;

import java.util.function.BiFunction;

/**
 * Functional interface describing state changes made on an entity of type {@code T} based on a given {@link EventMessage}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 * @param <T> The entity to apply the event state to.
 */
@FunctionalInterface
public interface EventStateApplier<T> extends BiFunction<EventMessage<?>, T, T> {

    /**
     * Change the state of the given {@code currentState} by applying the given {@code event} to it.
     *
     * @param event The event that might adjust the {@code currentState}.
     * @param currentState The current state of the entity to apply the given {@code event} to.
     * @return The changed stated based on the given {@code event}.
     */
    default T changeState(EventMessage<?> event, T currentState) {
        return apply(event, currentState);
    }
}

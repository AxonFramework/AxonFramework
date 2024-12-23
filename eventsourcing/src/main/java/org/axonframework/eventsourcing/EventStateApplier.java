package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventMessage;

import java.util.function.BiFunction;
import javax.annotation.Nonnull;

/**
 * Functional interface describing state changes made on a model of type {@code M} based on a given
 * {@link EventMessage}.
 *
 * @param <M> The model to apply the event state to.
 * @author Steven van Beelen
 * @since 5.0.0
 */
@FunctionalInterface
public interface EventStateApplier<M> extends BiFunction<M, EventMessage<?>, M> {

    /**
     * Change the state of the given {@code model} by applying the given {@code event} to it.
     *
     * @param event The event that might adjust the {@code model}.
     * @param model The current state of the entity to apply the given {@code event} to.
     * @return The changed stated based on the given {@code event}.
     */
    default M changeState(@Nonnull M model, @Nonnull EventMessage<?> event) {
        return apply(model, event);
    }
}

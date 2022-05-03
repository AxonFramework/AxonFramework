package org.axonframework.modelling.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface to describe a way to create Aggregate root instances based on an identifier when an instance has to be
 * created in combination to be used in Command handlers annotated with {@link CreationPolicy}
 *
 * @param <A> The type of aggregate this factory creates
 * @author Stefan Andjelkovic
 * @since 4.6
 */

@FunctionalInterface
public interface CreationPolicyAggregateFactory<A> {

    /**
     * Instantiates the aggregate root instance based on the provided identifier. The identifier can be a null if no
     * identifier is found or provided in the command message
     *
     * @param identifier the identifier extracted from the command message
     * @return an identifier initialized aggregate root instance ready to handle commands
     */
    @Nonnull A createAggregateRoot(@Nullable Object identifier);
}

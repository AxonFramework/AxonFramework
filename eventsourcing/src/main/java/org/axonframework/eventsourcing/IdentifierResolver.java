package org.axonframework.eventsourcing;

import java.util.function.Function;

/**
 * Functional interface describing a resolver of an identifier of type {@code ID} to {@link String}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 * @param <ID> The type of identifier to resolve to a {@link String}.
 */
@FunctionalInterface
public interface IdentifierResolver<ID> extends Function<ID, String> {

    /**
     * Resolves the given {@code identifier} to a {@link String} representation.
     *
     * @param identifier The instance to resolve to a {@link String} representation.
     * @return The given {@code identifier} resolved to a {@link String} representation.
     */
    default String resolve(ID identifier) {
        return apply(identifier);
    }
}

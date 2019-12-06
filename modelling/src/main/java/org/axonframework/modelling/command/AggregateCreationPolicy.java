package org.axonframework.modelling.command;

/**
 * Enumeration containing the possible creation policies for aggregates.
 *
 * @author Marc Gathier
 * @since 4.3
 */
public enum AggregateCreationPolicy {
    ALWAYS,
    CREATE_IF_MISSING,
    NEVER
}

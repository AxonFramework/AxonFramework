package org.axonframework.modelling.command;

/**
 * Enumeration containing the possible creation policies for aggregates.
 *
 * @author Marc Gathier
 * @since 4.3
 */
public enum AggregateCreationPolicy {
    /**
     * Always create a new instance of the aggregate on invoking the method. Fail if already exists.
     */
    ALWAYS,
    /**
     * Create a new instance of the aggregate when it is not found.
     */
    CREATE_IF_MISSING,
    /**
     * Expect instance of the aggregate to exist. Fail if missing.
     */
    NEVER
}

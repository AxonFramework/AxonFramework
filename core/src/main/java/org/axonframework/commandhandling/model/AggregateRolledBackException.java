package org.axonframework.commandhandling.model;

import org.axonframework.common.AxonException;

/**
 * Exception indicating that an aggregate has been part of a Unit of Work that was rolled back and that the validity of
 * its state cannot be guaranteed.
 * <p>
 * This typically occurs when multiple nested Units of Work operate on the same Aggregate instance, and that one of
 * these Units of Work has been rolled back. In such case, the parent Unit of Work cannot guarantee that the state of
 * the Aggregate is correct, and should (in such case) be rolled back.
 */
public class AggregateRolledBackException extends AxonException {

    private final String aggregateIdentifier;

    /**
     * Initialize the exception for an aggregate with given {@code aggregateIdentifier}.
     *
     * @param aggregateIdentifier The identifier of the compromised aggregate
     */
    public AggregateRolledBackException(String aggregateIdentifier) {
        super("Aggregate with id [" + aggregateIdentifier + "] was potentially modified in a Unit of Work that was " +
                      "rolled back. Saving its current state is unsafe.");
        this.aggregateIdentifier = aggregateIdentifier;
    }

    /**
     * Returns the identifier of the compromised aggregate.
     *
     * @return the identifier of the compromised aggregate
     */
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}

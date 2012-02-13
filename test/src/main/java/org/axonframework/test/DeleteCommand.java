package org.axonframework.test;

import org.axonframework.domain.AggregateIdentifier;

/**
 *
 */
public class DeleteCommand {

    private final AggregateIdentifier aggregateIdentifier;

    public DeleteCommand(AggregateIdentifier aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
    }

    public AggregateIdentifier getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}

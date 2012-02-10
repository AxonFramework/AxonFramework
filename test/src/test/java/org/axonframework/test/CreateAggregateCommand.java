package org.axonframework.test;

import org.axonframework.domain.AggregateIdentifier;

/**
 * @author Allard Buijze
 */
class CreateAggregateCommand {

    private final AggregateIdentifier aggregateIdentifier;

    public CreateAggregateCommand(AggregateIdentifier aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
    }

    public AggregateIdentifier getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}

package org.axonframework.test;

import org.axonframework.domain.AggregateIdentifier;

/**
 * @author Allard Buijze
 */
public class IllegalStateChangeCommand {

    private final AggregateIdentifier aggregateIdentifier;
    private final Integer newIllegalValue;

    public IllegalStateChangeCommand(AggregateIdentifier aggregateIdentifier, Integer newIllegalValue) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.newIllegalValue = newIllegalValue;
    }

    public AggregateIdentifier getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public Integer getNewIllegalValue() {
        return newIllegalValue;
    }
}

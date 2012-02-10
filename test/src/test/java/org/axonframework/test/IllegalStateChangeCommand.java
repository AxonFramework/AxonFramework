package org.axonframework.test;

/**
 * @author Allard Buijze
 */
public class IllegalStateChangeCommand {

    private final Object aggregateIdentifier;
    private final Integer newIllegalValue;

    public IllegalStateChangeCommand(Object aggregateIdentifier, Integer newIllegalValue) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.newIllegalValue = newIllegalValue;
    }

    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public Integer getNewIllegalValue() {
        return newIllegalValue;
    }
}

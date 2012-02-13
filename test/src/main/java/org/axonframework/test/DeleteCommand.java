package org.axonframework.test;

/**
 *
 */
public class DeleteCommand {

    private final Object aggregateIdentifier;

    public DeleteCommand(Object aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
    }

    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}

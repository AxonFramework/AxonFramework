package org.axonframework.test;

/**
 * @author Allard Buijze
 */
class CreateAggregateCommand {

    private final Object aggregateIdentifier;

    public CreateAggregateCommand(Object aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier;
    }

    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}

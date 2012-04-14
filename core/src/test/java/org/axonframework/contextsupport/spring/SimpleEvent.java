package org.axonframework.contextsupport.spring;

/**
 * @author Allard Buijze
 */
public class SimpleEvent {

    private String aggregateIdentifier;


    public SimpleEvent(Object aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier.toString();
    }

    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}

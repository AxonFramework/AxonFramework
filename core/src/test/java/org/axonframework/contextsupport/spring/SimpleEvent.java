package org.axonframework.contextsupport.spring;

import org.axonframework.domain.AggregateIdentifier;

/**
 * @author Allard Buijze
 */
public class SimpleEvent {

    private String aggregateIdentifier;


    public SimpleEvent(AggregateIdentifier aggregateIdentifier) {
        this.aggregateIdentifier = aggregateIdentifier.asString();
    }

    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}

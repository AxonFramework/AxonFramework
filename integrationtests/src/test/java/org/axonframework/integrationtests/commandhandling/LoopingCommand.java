package org.axonframework.integrationtests.commandhandling;

import org.axonframework.domain.AggregateIdentifier;

/**
 * @author Allard Buijze
 */
public class LoopingCommand {
    private final AggregateIdentifier aggregateId;

    public LoopingCommand(AggregateIdentifier aggregateId) {
        this.aggregateId = aggregateId;
    }

    public AggregateIdentifier getAggregateId() {
        return aggregateId;
    }
}

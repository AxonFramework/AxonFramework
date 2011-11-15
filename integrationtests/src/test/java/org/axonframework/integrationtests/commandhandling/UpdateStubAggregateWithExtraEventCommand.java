package org.axonframework.integrationtests.commandhandling;

import org.axonframework.domain.AggregateIdentifier;

/**
 * @author Allard Buijze
 */
public class UpdateStubAggregateWithExtraEventCommand {

    private AggregateIdentifier aggregateId;

    public UpdateStubAggregateWithExtraEventCommand(AggregateIdentifier aggregateId) {
        this.aggregateId = aggregateId;
    }

    public AggregateIdentifier getAggregateId() {
        return aggregateId;
    }
}

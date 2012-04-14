package org.axonframework.integrationtests.commandhandling;

/**
 * @author Allard Buijze
 */
public class UpdateStubAggregateWithExtraEventCommand {

    private Object aggregateId;

    public UpdateStubAggregateWithExtraEventCommand(Object aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Object getAggregateId() {
        return aggregateId;
    }
}

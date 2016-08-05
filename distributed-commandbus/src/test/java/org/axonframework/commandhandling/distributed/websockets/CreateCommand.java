package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.eventsourcing.AggregateIdentifier;

/**
 * Created by axon on 5-8-16.
 */
public class CreateCommand {
    @AggregateIdentifier
    private final String aggregate;

    public CreateCommand(String aggregate) {
        this.aggregate = aggregate;
    }

    public String getAggregate() {
        return aggregate;
    }
}

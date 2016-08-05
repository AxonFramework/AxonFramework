package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.eventsourcing.AggregateIdentifier;

/**
 * Created by axon on 5-8-16.
 */
public class TestCommand {
    @AggregateIdentifier
    private final String aggregate;

    private final String input;

    public TestCommand(String aggregate, String input) {
        this.aggregate = aggregate;
        this.input = input;
    }

    public String getAggregate() {
        return aggregate;
    }

    public String getInput() {
        return input;
    }
}

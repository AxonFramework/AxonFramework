package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by axon on 5-8-16.
 */
public class EchoCommandHandler {
    private String id;

    @CommandHandler
    public EchoCommandHandler(CreateCommand command) {
        apply(command);
    }

    @EventHandler
    public void handle(CreateCommand command) {
        this.id = command.getAggregate();
    }

    @CommandHandler
    public String handleCommand(TestCommand command) {
        return command.getInput().concat(command.getInput());
    }
}

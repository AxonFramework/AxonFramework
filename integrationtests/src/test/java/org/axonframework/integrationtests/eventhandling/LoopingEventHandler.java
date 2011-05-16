package org.axonframework.integrationtests.eventhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.integrationtests.commandhandling.LoopingChangeDoneEvent;
import org.axonframework.integrationtests.commandhandling.UpdateStubAggregateCommand;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Allard Buijze
 */

public class LoopingEventHandler {

    private CommandBus commandBus;

    @EventHandler
    public void handleLoopingEvent(LoopingChangeDoneEvent event) {
        commandBus.dispatch(new UpdateStubAggregateCommand(event.getAggregateIdentifier()));
    }

    @Autowired
    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
    }
}

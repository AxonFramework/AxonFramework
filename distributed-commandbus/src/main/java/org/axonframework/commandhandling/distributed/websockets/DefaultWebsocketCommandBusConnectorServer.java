package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.serialization.Serializer;

public class DefaultWebsocketCommandBusConnectorServer extends AbstractWebsocketCommandBusConnectorServer {
    private final Serializer serializer;
    private final CommandBus commandBus;

    public DefaultWebsocketCommandBusConnectorServer(CommandBus commandBus, Serializer serializer) {
        this.serializer = serializer;
        this.commandBus = commandBus;
    }

    @Override
    public Serializer getSerializer() {
        return serializer;
    }

    @Override
    public CommandBus getCommandBus() {
        return commandBus;
    }
}

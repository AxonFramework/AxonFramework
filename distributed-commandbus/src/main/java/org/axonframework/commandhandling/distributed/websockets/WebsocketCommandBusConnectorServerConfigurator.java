package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.CommandBus;

import javax.websocket.server.ServerEndpointConfig;

public class WebsocketCommandBusConnectorServerConfigurator extends ServerEndpointConfig.Configurator {
    private static CommandBus localSegment;

    public static CommandBus getLocalSegment() {
        return localSegment;
    }

    public static void setLocalSegment(CommandBus localSegment) {
        WebsocketCommandBusConnectorServerConfigurator.localSegment = localSegment;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        try {
            if (endpointClass.isAssignableFrom(WebsocketCommandBusConnectorServer.class)) {
                return (T) new WebsocketCommandBusConnectorServer(localSegment);
            } else {
                return endpointClass.newInstance();
            }
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
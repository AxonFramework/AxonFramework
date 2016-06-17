package org.axonframework.commandhandling.distributed.websockets;

public interface SessionClosedListener {
    void SessionClosed(WebsocketCommandBusConnectorClient client);
}

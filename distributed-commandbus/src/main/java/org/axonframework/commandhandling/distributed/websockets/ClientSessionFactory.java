package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.distributed.CommandBusConnectorCommunicationException;

import javax.websocket.Session;

public interface ClientSessionFactory {
    Session createSession(WebsocketCommandBusConnectorClient endpoint) throws CommandBusConnectorCommunicationException;
}

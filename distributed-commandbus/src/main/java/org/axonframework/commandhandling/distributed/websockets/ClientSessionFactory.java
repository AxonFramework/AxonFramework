package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.distributed.CommandBusConnectorCommunicationException;

import javax.websocket.Session;

/**
 * A Factory for creating sessions to a Websocket server.
 *
 * @author Koen Lavooij
 */
public interface ClientSessionFactory {
    /**
     * Creates a websocket client sessions to the given endpoint. Its responsibility is creating the connection and
     * attaching a message listener in order to process command callback messages.
     *
     * It will use a AuthorizationConfigurator if specified to authenticate during handshake.
     *
     * @param endpoint The endpoint to connect to
     * @return The session to the endpoint
     * @throws CommandBusConnectorCommunicationException if a session cannot be created
     */
    Session createSession(WebsocketCommandBusConnectorClient endpoint) throws CommandBusConnectorCommunicationException;
}

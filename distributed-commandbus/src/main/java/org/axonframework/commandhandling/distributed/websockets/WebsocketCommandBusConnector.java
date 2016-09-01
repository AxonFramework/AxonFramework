/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.CommandDispatchException;
import org.axonframework.commandhandling.distributed.Member;
import org.axonframework.commandhandling.distributed.RemoteCommandHandlingException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;

import javax.websocket.*;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The WebsocketCommandBusConnector is an CommandBusConnector for the DistributedCommandBus. It will send messages
 * through Websockets.
 *
 * @author Koen Lavooij
 */
public class WebsocketCommandBusConnector implements CommandBusConnector, Closeable {
    public static final int MESSAGE_BUFFER_SIZE = 16 * 1024 * 1024;
    private final ConcurrentHashMap<URI, WebsocketCommandBusConnectorClient> clients = new ConcurrentHashMap<>();
    private final AuthorizationConfigurator authorizationConfigurator;
    private final WebSocketContainer container;
    private final CommandBus commandBus;

    public WebsocketCommandBusConnector(CommandBus commandBus) {
        this(commandBus, null, MESSAGE_BUFFER_SIZE);
    }

    public WebsocketCommandBusConnector(CommandBus commandBus, String username, String password) {
        this(commandBus, username, password, MESSAGE_BUFFER_SIZE);
    }

    public WebsocketCommandBusConnector(CommandBus commandBus, String username, String password, int messageSize) {
        this(commandBus, new AuthorizationConfigurator(username, password), messageSize);
    }

    WebsocketCommandBusConnector(CommandBus commandBus, AuthorizationConfigurator authorizationConfigurator, int messageSize) {
        this.commandBus = commandBus;
        this.authorizationConfigurator = authorizationConfigurator;

        container = ContainerProvider.getWebSocketContainer();
        container.setAsyncSendTimeout(0);
        container.setDefaultMaxSessionIdleTimeout(0);
        container.setDefaultMaxBinaryMessageBufferSize(messageSize);
        //we send binary messages only
        container.setDefaultMaxTextMessageBufferSize(1);
    }

    /**
     * Creates a connection to a websocket server and then wraps it in a WebsocketCommandBusConnectorClient
     * @param uri The URI of the endpoint of the server to connect to
     * @return The WebsocketCommandBusConnectorClient to use to communicate with the server.
     */
    private WebsocketCommandBusConnectorClient getClientTo(URI uri) {
        return clients.computeIfAbsent(uri, (key) -> {
            //Create the websocket configuration
            ClientEndpointConfig.Builder builder = ClientEndpointConfig.Builder.create();
            if (authorizationConfigurator != null) {
                builder.configurator(authorizationConfigurator);
            }
            return new WebsocketCommandBusConnectorClient((endpoint) -> {
                try {
                    //Create the connection to the server
                    Session session = container.connectToServer(endpoint, builder.build(), uri);
                    session.addMessageHandler(ByteBuffer.class, endpoint);
                    return session;
                } catch (IOException | DeploymentException e) {
                    throw new RemoteCommandHandlingException(
                            String.format("Failed to send a command to websocket endpoint [%s]", uri), e);
                }
            });
        });
    }

    @Override
    public <C> void send(Member destination, CommandMessage<? extends C> command) throws Exception {
        URI uri = destination.getConnectionEndpoint(URI.class)
                .orElseThrow(() -> new CommandDispatchException("The destination does not support the protocol required by this connector"));
        getClientTo(uri).send(command, null);
    }

    @Override
    public <C, R> void send(Member destination, CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        URI uri = destination.getConnectionEndpoint(URI.class)
                .orElseThrow(() -> new CommandDispatchException("The destination does not support the protocol required by this connector"));
        getClientTo(uri).send(command, callback);
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        return commandBus.subscribe(commandName, handler);
    }

    @Override
    public void close() throws IOException {
        for (WebsocketCommandBusConnectorClient websocketCommandBusConnectorClient : clients.values()) {
            websocketCommandBusConnectorClient.close();
        }

    }
}

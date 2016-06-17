package org.axonframework.commandhandling.distributed.websockets;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.RemoteCommandHandlingException;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebsocketCommandBusConnector implements CommandBusConnector<URI> {
    public static final int MESSAGE_BUFFER_SIZE = 16 * 1024 * 1024;
    private final ConcurrentHashMap<URI, WebsocketCommandBusConnectorClient> clients = new ConcurrentHashMap<>();
    private final AuthorizationConfigurator authorizationConfigurator;
    private final WebSocketContainer container;
    private final String path;
    private final boolean secure;

    public WebsocketCommandBusConnector(boolean secure, String path, String username, String password, int messageSize) {
        this(secure, path, new AuthorizationConfigurator(username, password), messageSize);
    }

    public WebsocketCommandBusConnector(boolean secure, String path, String username, String password) {
        this(secure, path, username, password, MESSAGE_BUFFER_SIZE);
    }

    public WebsocketCommandBusConnector(boolean secure, String path) {
        this(secure, path, null, MESSAGE_BUFFER_SIZE);
    }

    WebsocketCommandBusConnector(boolean secure, String path, AuthorizationConfigurator authorizationConfigurator, int messageSize) {
        this.secure = secure;
        this.path = path;
        this.authorizationConfigurator = authorizationConfigurator;

        container = ContainerProvider.getWebSocketContainer();
        container.setAsyncSendTimeout(-1);
        container.setDefaultMaxSessionIdleTimeout(-1);
        container.setDefaultMaxBinaryMessageBufferSize(messageSize);
        container.setDefaultMaxTextMessageBufferSize(messageSize);
    }

    private WebsocketCommandBusConnectorClient getClientTo(URI uri) {
        return clients.computeIfAbsent(uri, (key) -> {
            ClientEndpointConfig.Builder builder = ClientEndpointConfig.Builder.create();
            if (authorizationConfigurator != null) {
                builder.configurator(authorizationConfigurator);
            }
            return new WebsocketCommandBusConnectorClient((endpoint) -> {
                try {
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
    public URI getLocalEndpoint() {
        //we do not know the local endpoint
        return null;
    }

    @Override
    public <C> void send(URI destination, CommandMessage<? extends C> command) throws Exception {
        getClientTo(getDestination(destination)).send(command, null);
    }

    @Override
    public <C, R> void send(URI destination, CommandMessage<C> command, CommandCallback<? super C, R> callback) {
        getClientTo(getDestination(destination)).send(command, callback);
    }

    private URI getDestination(URI root) {
        try {
            if (path == null || path.isEmpty()) {
                return new URI(secure ? "wss" : "ws", null, root.getHost(), root.getPort(), root.getPath(), null, null);
            } else {
                return new URI(secure ? "wss" : "ws", null, root.getHost(), root.getPort(), root.getPath() + path, null, null);
            }
        } catch (URISyntaxException e) {
            throw new RemoteCommandHandlingException("Could not build URI", e);
        }
    }

    @Override
    public void updateMembers(Set<URI> newMembers) {
        //
    }

    @Override
    public int getLoadFactor() {
        return 100;
    }
}

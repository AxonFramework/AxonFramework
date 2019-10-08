package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public class ActiveGrpcChannelCheck implements ConnectionSanityCheck {

    private final AxonServerConnectionManager connectionManager;
    private final String context;

    public ActiveGrpcChannelCheck(AxonServerConnectionManager connectionManager, String context) {
        this.connectionManager = connectionManager;
        this.context = context;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Detects if exists a gRPC channel between the client an Axon Server.
     *
     * @return true if the gRPC channel is opened, false otherwise
     */
    @Override
    public boolean isValid() {
        return connectionManager.isConnected(context);
    }
}

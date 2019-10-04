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

    @Override
    public boolean isValid() {
        return connectionManager.isConnected(context);
    }
}

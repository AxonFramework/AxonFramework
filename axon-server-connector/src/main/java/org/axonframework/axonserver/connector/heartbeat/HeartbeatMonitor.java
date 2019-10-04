package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public class HeartbeatMonitor {

    private final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final Runnable onInvalidConnection;

    private final ConnectionSanityCheck connectionSanityCheck;


    public HeartbeatMonitor(AxonServerConnectionManager connectionManager,
                            String context) {
        this(() -> connectionManager.forceDisconnection(context, new RuntimeException("Inactivity timeout.")),
             new HeartbeatConnectionCheck(connectionManager, context));
    }

    public HeartbeatMonitor(Runnable onInvalidConnection, ConnectionSanityCheck connectionSanityCheck) {
        this.onInvalidConnection = onInvalidConnection;
        this.connectionSanityCheck = connectionSanityCheck;
    }

    public void run() {
        try {
            boolean valid = connectionSanityCheck.isValid();
            if (!valid) {
                onInvalidConnection.run();
            }
        } catch (Exception e) {
            log.warn("Impossible to correctly monitor the Axon Server connection state.");
        }
    }
}

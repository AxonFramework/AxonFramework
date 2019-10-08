package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies if the connection is still alive, and react if it is not.
 *
 * @author Sara Pellegrini
 * @since 4.2
 */
public class HeartbeatMonitor {

    private final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final Runnable onInvalidConnection;

    private final ConnectionSanityCheck connectionSanityCheck;

    /**
     * Constructs an instance of {@link HeartbeatMonitor} that force a disconnection
     * when the AxonServer connection is no more alive.
     *
     * @param connectionManager connectionManager to AxonServer
     * @param context           the context
     */
    public HeartbeatMonitor(AxonServerConnectionManager connectionManager,
                            String context) {
        this(() -> connectionManager.forceDisconnection(context, new RuntimeException("Inactivity timeout.")),
             new HeartbeatConnectionCheck(connectionManager, context));
    }

    /**
     * Primary constructor of {@link HeartbeatMonitor}.
     *
     * @param onInvalidConnection callback to be call when the connection is no more alive
     * @param connectionSanityCheck sanity check which allows to verify if the connection is alive
     */
    public HeartbeatMonitor(Runnable onInvalidConnection, ConnectionSanityCheck connectionSanityCheck) {
        this.onInvalidConnection = onInvalidConnection;
        this.connectionSanityCheck = connectionSanityCheck;
    }

    /**
     * Verify if the connection with AxonServer is still alive.
     * If it is not, invoke a callback in order to react to the disconnection.
     */
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

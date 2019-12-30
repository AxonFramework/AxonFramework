package org.axonframework.axonserver.connector.heartbeat;

/**
 * Represents the mechanism used to send an heartbeat message to AxonServer.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public interface HeartbeatSource {

    /**
     * Send an heartbeat to AxonServer
     */
    void pulse();
}

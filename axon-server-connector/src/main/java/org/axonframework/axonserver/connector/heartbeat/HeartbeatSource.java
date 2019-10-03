package org.axonframework.axonserver.connector.heartbeat;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public interface HeartbeatSource {

    void send();
}

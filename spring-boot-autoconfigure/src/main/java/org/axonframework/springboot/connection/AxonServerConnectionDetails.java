package org.axonframework.springboot.connection;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

/**
 * ConnectionDetails implementation carrying the connection details for an Axon Server instance.
 * <p>
 * Note that this is not a replacement for full connectivity configuration. ConnectionDetails are designed to only carry
 * the endpoint at which a node runs.
 *
 * @author Allard Buijze
 * @since 4.9
 */
public interface AxonServerConnectionDetails extends ConnectionDetails {

    String routingServers();

}

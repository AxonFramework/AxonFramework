package org.axonframework.springboot.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * ConnectionDetailsFactory implementation that recognizes Spring Boot Docker Compose-managed Docker containers running
 * Axon Server and creates an AxonServerConnectionDetails bean with the details to connect to AxonServer within that
 * container.
 *
 * @author Allard Buijze
 * @since 4.9
 */
public class AxonServerDockerComposeConnectionDetailsFactory extends DockerComposeConnectionDetailsFactory<AxonServerConnectionDetails> {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerDockerComposeConnectionDetailsFactory.class);

    /**
     * Initializes the factory to look for containers running the "axoniq/axonserver" image.
     */
    public AxonServerDockerComposeConnectionDetailsFactory() {
        super("axoniq/axonserver", "io.axoniq.axonserver.connector.AxonServerConnectionFactory");
    }

    @Override
    protected AxonServerConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
        int port = source.getRunningService().ports().get(8124);
        int uiPort = source.getRunningService().ports().get(8024);
        String host = source.getRunningService().host();

        //noinspection HttpUrlsUsage
        logger.info("Detected Axon Server container. To access the dashboard, visit http://{}:{}", host, uiPort);

        return () -> host + ":" + port;
    }

}

package org.axonframework.springboot.connection;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class SpringBootTestContainerIntegrationTest {

    @Container
    @ServiceConnection
    public static AxonServerContainer axonServer = new AxonServerContainer("axoniq/axonserver:latest-dev")
            .withDevMode(true);

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    AxonServerConfiguration axonServerConfiguration;

    @Autowired
    AxonServerConnectionDetails connectionDetails;

    @Test
    void verifyApplicationStartsNormallyWithAxonServerInstance() {
        assertTrue(axonServer.isRunning());
        assertNotNull(connectionDetails);
        assertTrue(connectionDetails.routingServers().endsWith("" + axonServer.getGrpcPort()));
        assertNotNull(axonServerConfiguration);

        assertNotEquals("localhost:8024", axonServerConfiguration.getServers());
    }
}
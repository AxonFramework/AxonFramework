package org.axonframework.springboot.connection;

import io.axoniq.axonserver.connector.AxonServerConnection;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.springboot.service.connection.AxonServerConnectionDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootDockerComposeIntegrationTest {

    private ConfigurableApplicationContext application;

    @BeforeEach
    void setUp() {
        application = SpringApplication.run(SpringBootApplication.class,
                                            "--spring.docker.compose.file=test-docker-compose.yml",
                                            "--spring.docker.compose.skip.in-tests=false");
    }

    @AfterEach
    void tearDown() {
        application.stop();
    }

    @Test
    void verifyApplicationRunsAndConnectsToAxonServerDefinedInDockerComposeFile() {
        assertTrue(application.isRunning());
        AxonServerConfiguration config = application.getBean(AxonServerConfiguration.class);

        assertNotNull(application.getBean(AxonServerConnectionDetails.class),
                      "Expected an AxonServerConnectionDetails bean pointing to Axon Server in Docker");

        assertNotNull(config);
        assertNotEquals("localhost:8124", config.getServers());
        assertNotEquals("localhost", config.getServers());

        AxonServerConnectionManager connectionFactory = application.getBean(AxonServerConnectionManager.class);
        AxonServerConnection connection = connectionFactory.getConnection();

        await().atMost(Duration.ofMillis(5))
               .untilAsserted(() -> assertTrue(connection.isConnected()));
    }
}

package org.axonframework.axonserver.connector.event.axon;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.StubServer;
import org.axonframework.axonserver.connector.util.TcpUtil;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class EventProcessorInfoConfigurationTest {

    private StubServer stubServer;
    private int port;
    private Configuration configuration;

    @BeforeEach
    void setUp() throws IOException {
        port = TcpUtil.findFreePort();
        stubServer = new StubServer(port);
        stubServer.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (configuration != null) {
            configuration.shutdown();
        }
        stubServer.shutdown();
    }

    @Test
    void noActionShouldBeTakenWhenThereIsNoEventProcessingConfiguration() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration()
                                                 .registerComponent(AxonServerConfiguration.class,
                                                                    c -> AxonServerConfiguration.builder()
                                                                                                .servers("localhost:" + port)
                                                                                                .connectTimeout(1000)
                                                                                                .build());

        configuration = configurer.buildConfiguration();
        Assertions.assertDoesNotThrow(() -> configuration.start());
        configuration.shutdown();
    }
}
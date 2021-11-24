package org.axonframework.test.server;

import org.junit.jupiter.api.*;
import org.testcontainers.utility.DockerImageName;

/**
 * Simple test class for AxonServerSEContainer.
 */
class AxonServerSEContainerTest {

    @Test
    void supportsAxonServer_4_4_X() {
        try (
                final AxonServerSEContainer axonServerSEContainer =
                        new AxonServerSEContainer(DockerImageName.parse("axoniq/axonserver:4.4.12"))
        ) {
            axonServerSEContainer.start();
        }
    }

    @Test
    void supportsAxonServer_4_5_X() {
        try (
                final AxonServerSEContainer axonServerSEContainer =
                        new AxonServerSEContainer(DockerImageName.parse("axoniq/axonserver:4.5.8"))
        ) {
            axonServerSEContainer.start();
        }
    }
}

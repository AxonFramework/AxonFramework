package org.axonframework.test.server;

import org.junit.jupiter.api.*;
import org.testcontainers.utility.DockerImageName;

/**
 * Simple test class for AxonServerEEContainer.
 */
class AxonServerEEContainerTest {

    @Test
    void supportsAxonServer_4_5_X() {
        try (
                final AxonServerEEContainer axonServerEEContainer =
                        new AxonServerEEContainer(DockerImageName.parse("axoniq/axonserver-enterprise:4.5.9-dev"))
        ) {
            axonServerEEContainer.start();
        }
    }
}

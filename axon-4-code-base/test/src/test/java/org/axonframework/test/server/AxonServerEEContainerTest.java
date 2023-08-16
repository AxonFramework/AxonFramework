/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.server;

import org.junit.jupiter.api.*;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test class for AxonServerEEContainer.
 *
 * @author Lucas Campos
 */
class AxonServerEEContainerTest {

    @Test
    void supportsAxonServer_4_5_X() {
        try (
                final AxonServerEEContainer axonServerEEContainer =
                        new AxonServerEEContainer(DockerImageName.parse("axoniq/axonserver-enterprise:4.5.9-dev"))
        ) {
            axonServerEEContainer.start();
            assertTrue(axonServerEEContainer.isRunning());
        }
    }

    @Test
    void axonServer_4_5_X_genericTest() {
        try (
                final AxonServerEEContainer axonServerEEContainer =
                        new AxonServerEEContainer(DockerImageName.parse("axoniq/axonserver-enterprise:4.5.9-dev"))
                                .withAxonServerName("axon-server-name")
                                .withAxonServerInternalHostname("axon-server-internal-host-name")
                                .withAxonServerHostname("axon-server-hostname")
        ) {
            axonServerEEContainer.start();
            assertTrue(axonServerEEContainer.isRunning());
            assertNotNull(axonServerEEContainer.getAxonServerAddress());
            assertNotNull(axonServerEEContainer.getGrpcPort());
            assertNotNull(axonServerEEContainer.getHost());
            assertEquals(2, axonServerEEContainer.getExposedPorts().size());
            assertEquals("axon-server-name", axonServerEEContainer.getEnvMap().get("AXONIQ_AXONSERVER_NAME"));
            assertEquals("axon-server-internal-host-name",
                         axonServerEEContainer.getEnvMap().get("AXONIQ_AXONSERVER_INTERNAL_HOSTNAME"));
            assertEquals("axon-server-hostname", axonServerEEContainer.getEnvMap().get("AXONIQ_AXONSERVER_HOSTNAME"));
        }
    }
}

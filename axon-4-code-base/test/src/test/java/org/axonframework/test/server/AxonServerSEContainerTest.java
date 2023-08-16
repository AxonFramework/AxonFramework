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
 * Simple test class for AxonServerSEContainer.
 *
 * @author Lucas Campos
 */
class AxonServerSEContainerTest {

    @Test
    void supportsAxonServer_4_4_X() {
        try (
                final AxonServerSEContainer axonServerSEContainer =
                        new AxonServerSEContainer(DockerImageName.parse("axoniq/axonserver:4.4.12"))
        ) {
            axonServerSEContainer.start();
            assertTrue(axonServerSEContainer.isRunning());
        }
    }

    @Test
    void supportsAxonServer_4_5_X() {
        try (
                final AxonServerSEContainer axonServerSEContainer =
                        new AxonServerSEContainer(DockerImageName.parse("axoniq/axonserver:4.5.8-dev"))
        ) {
            axonServerSEContainer.start();
            assertTrue(axonServerSEContainer.isRunning());
        }
    }

    @Test
    void axonServer_latest_genericTest() {
        try (
                final AxonServerSEContainer axonServerSEContainer =
                        new AxonServerSEContainer(DockerImageName.parse("axoniq/axonserver:latest-dev"))
                                .withDevMode(true)
        ) {
            axonServerSEContainer.start();
            assertTrue(axonServerSEContainer.isRunning());
            assertNotNull(axonServerSEContainer.getAxonServerAddress());
            assertNotNull(axonServerSEContainer.getGrpcPort());
            assertNotNull(axonServerSEContainer.getHost());
            assertEquals(2, axonServerSEContainer.getExposedPorts().size());
            assertEquals("true", axonServerSEContainer.getEnvMap().get("AXONIQ_AXONSERVER_DEVMODE_ENABLED"));
        }
    }
}

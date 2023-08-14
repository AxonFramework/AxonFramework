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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test class for {@link AxonServerContainer}.
 *
 * @author Lucas Campos
 * @author Steven van Beelen
 */
class AxonServerContainerTest {

    @Test
    void constructionThroughStringImageNameStartsAsExpected() {
        String testName = "axoniq/axonserver:latest-dev";
        try (
                AxonServerContainer testSubject = new AxonServerContainer(testName)
        ) {
            testSubject.start();
            assertTrue(testSubject.isRunning());
        }
    }

    @Test
    void constructionThroughDockerImageNameStartsAsExpected() {
        DockerImageName testName = DockerImageName.parse("axoniq/axonserver:latest-dev");
        try (
                AxonServerContainer testSubject = new AxonServerContainer(testName)
        ) {
            testSubject.start();
            assertTrue(testSubject.isRunning());
            assertNotNull(testSubject.getAxonServerAddress());
            assertNotNull(testSubject.getGrpcPort());
            assertNotNull(testSubject.getHost());
            List<Integer> resultExposedPorts = testSubject.getExposedPorts();
            assertTrue(resultExposedPorts.contains(8024));
            assertTrue(resultExposedPorts.contains(8124));
        }
    }

    @Test
    void fullyCustomizedAxonServerContainerStartsAsExpected() {
        String testName = "axon-server-name";
        String testHostName = "axon-server-hostname";
        String testInternalHostName = "axon-server-internal-host-name";
        boolean testDevMode = true;
        try (
                AxonServerContainer testSubject =
                        new AxonServerContainer("axoniq/axonserver:latest-dev")
                                .withAxonServerName(testName)
                                .withAxonServerHostname(testHostName)
                                .withAxonServerInternalHostname(testInternalHostName)
                                .withDevMode(testDevMode)
        ) {
            testSubject.start();
            assertTrue(testSubject.isRunning());

            assertEquals(testName, testSubject.getEnvMap().get("AXONIQ_AXONSERVER_NAME"));
            assertEquals(testInternalHostName, testSubject.getEnvMap().get("AXONIQ_AXONSERVER_INTERNAL_HOSTNAME"));
            assertEquals(testHostName, testSubject.getEnvMap().get("AXONIQ_AXONSERVER_HOSTNAME"));
            assertEquals(Boolean.toString(testDevMode),
                         testSubject.getEnvMap().get("AXONIQ_AXONSERVER_DEVMODE_ENABLED"));
        }
    }
}

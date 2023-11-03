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

package org.axonframework.springboot.connection;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.springboot.service.connection.AxonServerConnectionDetails;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class SpringBootTestContainerIntegrationTest {

    @Container
    @ServiceConnection
    static AxonServerContainer axonServer = new AxonServerContainer("axoniq/axonserver:latest-dev")
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
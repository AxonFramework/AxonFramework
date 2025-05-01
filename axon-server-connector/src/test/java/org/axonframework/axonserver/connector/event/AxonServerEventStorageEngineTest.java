/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.connector.impl.ServerAddress;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.StorageEngineTestSuite;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite implementation validating the {@link AxonServerEventStorageEngine}.
 *
 * @author Steven van Beelen
 */
@Testcontainers
class AxonServerEventStorageEngineTest extends StorageEngineTestSuite<AxonServerEventStorageEngine> {

    private static final String CONTEXT = "default";

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> axonServerContainer =
            new GenericContainer<>(getDockerImageName())
                    .withExposedPorts(8024, 8124)
                    .withEnv("AXONIQ_AXONSERVER_HOSTNAME", "localhost")
                    .withEnv("AXONIQ_AXONSERVER_DEVMODE_ENABLED", "true")
                    .waitingFor(Wait.forLogMessage(".*Started AxonServer.*", 1))
                    .waitingFor(Wait.forHttp("/actuator/health").forPort(8024));

    private static String getDockerImageName() {
        String envVariable = System.getenv("AXON_SERVER_IMAGE");
        return envVariable != null ? envVariable : System.getProperty("AXON_SERVER_IMAGE", "axoniq/axonserver");
    }

    private static AxonServerConnection connection;

    @BeforeAll
    static void beforeAll() {
        axonServerContainer.start();
        ServerAddress serverAddress = new ServerAddress(axonServerContainer.getHost(),
                                                        axonServerContainer.getMappedPort(8124));
        connection = AxonServerConnectionFactory.forClient("AxonServerEventStorageEngineTest")
                                                .routingServers(serverAddress)
                                                .build()
                                                .connect(CONTEXT);
    }

    @AfterAll
    static void afterAll() {
        connection.disconnect();
        axonServerContainer.stop();
    }

    @Override
    protected AxonServerEventStorageEngine buildStorageEngine() throws IOException {
        // TODO replace for create and delete context
//        AxonServerUtils.purgeEventsFromAxonServer(axonServerContainer.getHost(),
//                                                  axonServerContainer.getMappedPort(8124),
//                                                  CONTEXT);
        return new AxonServerEventStorageEngine(connection, new TestConverter());
    }

    @Test
    void describeTo() {
        MockComponentDescriptor descriptor = new MockComponentDescriptor();

        testSubject.describeTo(descriptor);

        Map<String, Object> describedProperties = descriptor.getDescribedProperties();
        assertEquals(2, describedProperties.size());
        assertTrue(describedProperties.containsKey("connection"));
        assertTrue(describedProperties.containsKey("converter"));
    }
}
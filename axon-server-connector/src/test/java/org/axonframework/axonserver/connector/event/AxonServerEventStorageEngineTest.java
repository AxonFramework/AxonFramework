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
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite implementation validating the {@link AxonServerEventStorageEngine}.
 *
 * @author Steven van Beelen
 */
@Testcontainers
class AxonServerEventStorageEngineTest extends StorageEngineTestSuite<AxonServerEventStorageEngine> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String CONTEXT = "default";
    private static final String DEFAULT_SERVER_IMAGE = "axoniq/axonserver";

    @Container
    private static final AxonServerContainer container =
            new AxonServerContainer(getDockerImageName()).withDevMode(true);

    private static DockerImageName getDockerImageName() {
        String envVariable = System.getenv("AXON_SERVER_IMAGE");
        if (envVariable != null) {
            logger.debug("AXON_SERVER_IMAGE ENVIRONMENT VARIABLE FOUND");
            return DockerImageName.parse(envVariable).asCompatibleSubstituteFor(DEFAULT_SERVER_IMAGE);
        }
        String systemProperty = System.getProperty("AXON_SERVER_IMAGE");
        if (systemProperty != null) {
            logger.debug("AXON_SERVER_IMAGE SYSTEM PROPERTY FOUND");
            return DockerImageName.parse(systemProperty).asCompatibleSubstituteFor(DEFAULT_SERVER_IMAGE);
        }
        return DockerImageName.parse(DEFAULT_SERVER_IMAGE).asCompatibleSubstituteFor(DEFAULT_SERVER_IMAGE);
    }

    private static AxonServerConnection connection;

    @BeforeAll
    static void beforeAll() {
        container.start();
        ServerAddress address = new ServerAddress(container.getHost(), container.getGrpcPort());
        connection = AxonServerConnectionFactory.forClient("AxonServerEventStorageEngineTest")
                                                .routingServers(address)
                                                .build()
                                                .connect(CONTEXT);
    }

    @AfterAll
    static void afterAll() {
        connection.disconnect();
        container.stop();
    }

    @Override
    protected AxonServerEventStorageEngine buildStorageEngine() throws IOException {
        // TODO actual use purge once present
//        AxonServerContainerUtils.purgeEventsFromAxonServer(container.getHost(),
//                                                           container.getMappedPort(8124),
//                                                           CONTEXT,
//                                                           AxonServerContainerUtils.DCB_CONTEXT);
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

    // TODO #3421 enable once tokenAt is implemented
    @Disabled("#3421 enable once tokenAt is implemented")
    protected void tokenAtRetrievesTokenFromStorageEngineThatStreamsEventsSinceThatMoment() throws Exception {
        super.tokenAtRetrievesTokenFromStorageEngineThatStreamsEventsSinceThatMoment();
    }

    // TODO #3421 enable once tokenAt is implemented
    @Disabled("#3421 enable once tokenAt is implemented")
    protected void tokenAtReturnsHeadTokenWhenThereAreNoEventsAfterTheGivenAt() throws Exception {
        super.tokenAtReturnsHeadTokenWhenThereAreNoEventsAfterTheGivenAt();
    }
}
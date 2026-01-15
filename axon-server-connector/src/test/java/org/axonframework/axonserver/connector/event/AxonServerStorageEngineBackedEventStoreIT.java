/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStoreTestSuite;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite implementation validating the {@link AxonServerEventStorageEngine}.
 *
 * @author John Hendrikx
 */
@Testcontainers
class AxonServerStorageEngineBackedEventStoreIT extends StorageEngineBackedEventStoreTestSuite<AxonServerEventStorageEngine> {

    private static final String CONTEXT = "default";

    @SuppressWarnings("resource")
    @Container
    private static final AxonServerContainer container = new AxonServerContainer("docker.axoniq.io/axoniq/axonserver:2025.2.0")
        .withDevMode(true)
        .withDcbContext(true);

    private static AxonServerConnection connection;

    private static AxonServerEventStorageEngine engine;

    @BeforeAll
    static void buildEngine() {
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
    protected AxonServerEventStorageEngine getStorageEngine(EventConverter converter) {
        if (engine == null) {
            engine = new AxonServerEventStorageEngine(connection, converter);
        }

        return engine;
    }

    @Override
    protected void enhanceProcessingLifecycle(ProcessingLifecycle lifecycle) {
        // No enhancement needed for Axon Server, it does its own transaction management
    }

    @Test
    void describeTo() {
        MockComponentDescriptor descriptor = new MockComponentDescriptor();

        engine.describeTo(descriptor);

        Map<String, Object> describedProperties = descriptor.getDescribedProperties();
        assertEquals(2, describedProperties.size());
        assertTrue(describedProperties.containsKey("connection"));
        assertTrue(describedProperties.containsKey("converter"));
    }
}
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
import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStoreTestSuite;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite implementation validating the {@link AxonServerEventStorageEngine}.
 *
 * @author John Hendrikx
 */
@Testcontainers
class AxonServerStorageEngineBackedEventStoreIT
        extends StorageEngineBackedEventStoreTestSuite<AxonServerEventStorageEngine> {

    private static final UnitOfWorkFactory FACTORY = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);
    private static final String CONTEXT = "default";

    @SuppressWarnings("resource")
    @Container
    private static final AxonServerContainer container =
            new AxonServerContainer("docker.axoniq.io/axoniq/axonserver:2025.2.0")
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

    @Nonnull
    @Override
    protected AxonServerEventStorageEngine getStorageEngine(@Nonnull EventConverter converter) {
        if (engine == null) {
            engine = new AxonServerEventStorageEngine(connection, converter);
        }

        return engine;
    }

    @Nonnull
    @Override
    protected UnitOfWork unitOfWork() {
        return FACTORY.create();
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
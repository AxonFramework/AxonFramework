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

package org.axonframework.integrationtests.axonserverconnector;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.snapshot.AxonServerSnapshotStore;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.conversion.Converter;
import org.axonframework.eventsourcing.StoreBackedSnapshotterTestSuite;
import org.axonframework.eventsourcing.snapshot.store.SnapshotStore;
import org.axonframework.eventsourcing.snapshot.store.StoreBackedSnapshotter;
import org.axonframework.test.server.AxonServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests the {@link StoreBackedSnapshotter} with an {@link AxonServerSnapshotStore}.
 *
 * @author John Hendrikx
 */
@Testcontainers
public class AxonServerBackedSnapshotterIT extends StoreBackedSnapshotterTestSuite {

    @SuppressWarnings("resource")
    @Container
    private static final AxonServerContainer CONTAINER = new AxonServerContainer()
        .withDevMode(true)
        .withDcbContext(true);

    @Override
    protected void registerComponents(ComponentRegistry registry) {
        registry.registerComponent(AxonServerConfiguration.class, c -> AxonServerConfiguration.builder()
            .componentName("AxonServerBackedSnapshotterIT")
            .servers(CONTAINER.getAxonServerAddress())
            .build()
        );

        registry.registerComponent(SnapshotStore.class, c -> {
            AxonServerConnectionManager component = c.getComponent(AxonServerConnectionManager.class);

            return new AxonServerSnapshotStore(
                component.getConnection(),
                c.getComponent(Converter.class)
            );
        });
    }
}

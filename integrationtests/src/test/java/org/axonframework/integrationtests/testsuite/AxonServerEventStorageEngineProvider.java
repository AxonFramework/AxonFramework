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

package org.axonframework.integrationtests.testsuite;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * {@link EventStorageEngineProvider} that uses a Dockerized Axon Server as the event storage backend.
 * <p>
 * A single {@link AxonServerContainer} is shared across all tests via the JUnit 6 root store (started exactly once per
 * JVM run by {@link EventStorageEngineExtension}). The container is configured with dev mode and DCB context enabled,
 * and uses {@code withReuse(true)} for faster repeated runs when
 * {@code testcontainers.reuse.enable=true} is set in {@code ~/.testcontainers.properties}.
 * <p>
 * Call {@link AbstractIntegrationTest#purgeEventStorage()} in a test to reset the Axon Server event store to a clean
 * state.
 */
public class AxonServerEventStorageEngineProvider implements EventStorageEngineProvider {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerEventStorageEngineProvider.class);

    private static final AxonServerContainer CONTAINER =
            new AxonServerContainer("docker.axoniq.io/axoniq/axonserver:2025.2.0")
                    .withAxonServerHostname("localhost")
                    .withDevMode(true)
                    .withReuse(true)
                    .withDcbContext(true);

    @Override
    public void start() {
        CONTAINER.start();
        logger.info("Using Axon Server for integration tests. UI is available at http://localhost:{}",
                    CONTAINER.getHttpPort());
    }

    @Override
    public void configure(ComponentRegistry cr) {
        AxonServerConfiguration cfg = new AxonServerConfiguration();
        cfg.setServers(CONTAINER.getHost() + ":" + CONTAINER.getGrpcPort());
        cr.registerComponent(AxonServerConfiguration.class, c -> cfg);
    }

    @Override
    public void reset() {
        try {
            logger.info("Purging events from Axon Server.");
            AxonServerContainerUtils.purgeEventsFromAxonServer(
                    CONTAINER.getHost(),
                    CONTAINER.getHttpPort(),
                    "default",
                    AxonServerContainerUtils.DCB_CONTEXT
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to purge event storage", e);
        }
    }

    // close(): withReuse(true) — Ryuk skips reusable containers; they persist until Docker restart.
    // Cross-run reuse requires testcontainers.reuse.enable=true in ~/.testcontainers.properties.
    // Without it, the container still works within one JVM run but is removed by Ryuk at shutdown.
}

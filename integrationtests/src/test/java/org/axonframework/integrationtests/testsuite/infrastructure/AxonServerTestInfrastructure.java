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

package org.axonframework.integrationtests.testsuite.infrastructure;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * {@link TestInfrastructure} implementation that wires tests against a real Axon Server instance managed by
 * Testcontainers.
 * <p>
 * The underlying {@link AxonServerContainer} is a {@code static final} field, so it is shared across all instances of
 * this class and all leaf test classes that use it. {@link AxonServerContainer#start()} is idempotent — Testcontainers
 * makes it a no-op when the container is already running — so calling {@link #start()} from every {@code @BeforeEach}
 * is safe and cheap after the first test.
 * <p>
 * Leaf test classes should hold a {@code private static final} instance of this class:
 * <pre>{@code
 * private static final TestInfrastructure INFRASTRUCTURE = new AxonServerTestInfrastructure();
 *
 * @Override
 * protected TestInfrastructure createTestInfrastructure() {
 *     return INFRASTRUCTURE;
 * }
 * }</pre>
 */
public final class AxonServerTestInfrastructure implements TestInfrastructure {

    private static final Logger LOG = LoggerFactory.getLogger(AxonServerTestInfrastructure.class);

    private static final AxonServerContainer CONTAINER =
            new AxonServerContainer("docker.axoniq.io/axoniq/axonserver:2025.2.0")
                    .withAxonServerHostname("localhost")
                    .withDevMode(true)
                    .withReuse(true)
                    .withDcbContext(true);

    @Override
    public void start() {
        boolean wasRunning = CONTAINER.isRunning();
        CONTAINER.start();
        if (!wasRunning) {
            LOG.info("Axon Server UI at http://localhost:{}", CONTAINER.getHttpPort());
        }
    }

    @Override
    public void configureInfrastructure(ComponentRegistry registry) {
        AxonServerConfiguration config = new AxonServerConfiguration();
        config.setServers(CONTAINER.getHost() + ":" + CONTAINER.getGrpcPort());
        registry.registerComponent(AxonServerConfiguration.class, c -> config);
    }

    @Override
    public void purgeData() {
        try {
            LOG.info("Purging events from Axon Server.");
            AxonServerContainerUtils.purgeEventsFromAxonServer(
                    CONTAINER.getHost(),
                    CONTAINER.getHttpPort(),
                    "default",
                    AxonServerContainerUtils.DCB_CONTEXT
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to purge AxonServer event storage", e);
        }
    }

    @Override
    public void stop() {
        // The container is shared across the JVM (static final, withReuse(true)).
        // Testcontainers + Ryuk handle cleanup on JVM exit; stopping per test would
        // defeat reuse. No-op on purpose.
    }
}

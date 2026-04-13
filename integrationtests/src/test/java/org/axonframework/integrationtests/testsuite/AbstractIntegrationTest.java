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

import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Abstract base class for Axon Framework integration tests with a pluggable event storage backend.
 * <p>
 * The backend is determined by {@link TestEventStorageEngine} — no value means use the
 * {@code axon.test.storage-engine-provider} system property, or fall back to {@link InMemoryEventStorageEngineProvider}
 * (no Docker required). Subclasses that always need Axon Server extend {@link AbstractAxonServerIT} instead.
 * <p>
 * The {@link EventStorageEngineExtension} starts the selected provider exactly once per JVM run and injects it into
 * each test instance before the test runs.
 * <p>
 * Concrete implementations must provide an {@link ApplicationConfigurer} via {@link #createConfigurer()}. Call
 * {@link #startApp()} when the test setup is complete to start the Axon application. Call
 * {@link #purgeEventStorage()} before {@link #startApp()} to reset the event store to a clean state (only needed for
 * backends with persistent storage).
 *
 * @author Mitchell Herrijgers
 */
@ExtendWith(EventStorageEngineExtension.class)
@TestEventStorageEngine
public abstract class AbstractIntegrationTest {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    private static final Random RND = new Random();

    // Injected by EventStorageEngineExtension.postProcessTestInstance()
    EventStorageEngineProvider storageEngineProvider;

    protected CommandGateway commandGateway;
    protected AxonConfiguration startedConfiguration;

    @AfterEach
    void tearDown() {
        if (startedConfiguration != null) {
            startedConfiguration.shutdown();
        }
    }

    /**
     * Starts the Axon Framework application using the {@link ApplicationConfigurer} returned by
     * {@link #createConfigurer()} and the current backend provided by {@link #storageEngineProvider}.
     */
    protected void startApp() {
        startedConfiguration = createConfigurer()
                .componentRegistry(storageEngineProvider::configure)
                .start();
        commandGateway = startedConfiguration.getComponent(CommandGateway.class);
    }

    /**
     * Resets the event store to a clean state by delegating to {@link EventStorageEngineProvider#reset()}. Call this
     * before {@link #startApp()} when the test requires a clean event store. No-op for {@link InMemoryEventStorageEngineProvider}
     * — each {@code startApp()} call already produces a fresh engine.
     */
    protected void purgeEventStorage() {
        storageEngineProvider.reset();
    }

    /**
     * Creates the {@link ApplicationConfigurer} defining the Axon Framework test context.
     *
     * @return the {@link ApplicationConfigurer} defining the Axon Framework test context
     */
    protected abstract ApplicationConfigurer createConfigurer();

    /**
     * Creates a unique identifier with the given prefix.
     *
     * @param prefix the prefix to use
     * @return a unique identifier
     */
    protected static String createId(String prefix) {
        return prefix + "-" + RND.nextInt(Integer.MAX_VALUE);
    }
}

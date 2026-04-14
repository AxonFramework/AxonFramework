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

import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.integrationtests.testsuite.infrastructure.TestInfrastructure;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Infrastructure-agnostic base class for all integration tests in this suite.
 * <p>
 * Replaces the AxonServer-coupled {@code AbstractAxonServerIT}. The specific backend (AxonServer, in-memory, Postgres,
 * …) is supplied by leaf test classes through {@link #createTestInfrastructure()}.
 * <p>
 * Subclasses must implement:
 * <ul>
 *     <li>{@link #createTestInfrastructure()} — return the infrastructure strategy to use</li>
 *     <li>{@link #createConfigurer()} — return the domain-specific {@link ApplicationConfigurer}</li>
 * </ul>
 * and must call {@link #startApp()} (typically from a {@code @BeforeEach} method) to start the Axon configuration.
 *
 * @since 5.1.0
 */
@Internal
public abstract class AbstractIntegrationTest {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationTest.class);


    protected CommandGateway commandGateway;
    protected AxonConfiguration startedConfiguration;

    /**
     * Per-test cache of the {@link TestInfrastructure} wrapper, ensuring that {@link TestInfrastructure#start()} and
     * {@link TestInfrastructure#stop()} always operate on the same instance.
     */
    private TestInfrastructure cachedInfrastructure;

    /**
     * Factory method called once per test to obtain the {@link TestInfrastructure} for this test class. Leaf classes
     * typically return a {@code private static final} instance:
     * <pre>{@code
     * private static final TestInfrastructure INFRASTRUCTURE = new AxonServerTestInfrastructure();
     *
     * @Override
     * protected TestInfrastructure createTestInfrastructure() {
     *     return INFRASTRUCTURE;
     * }
     * }</pre>
     *
     * @return the infrastructure strategy to use for this test
     */
    protected abstract TestInfrastructure createTestInfrastructure();

    /**
     * Returns the domain-specific {@link ApplicationConfigurer} for this test. Called from {@link #startApp()} each
     * time the Axon configuration is started.
     *
     * @return the application configurer for this test
     */
    protected abstract ApplicationConfigurer createConfigurer();

    /**
     * Returns the cached {@link TestInfrastructure} for the current test, initializing it on first access.
     * <p>
     * This accessor is {@code final} to guarantee that all internal callers ({@link #startApp()},
     * {@link #purgeData()}, {@code @AfterEach}) share the same instance.
     *
     * @return the infrastructure wrapper for this test
     */
    protected final TestInfrastructure testInfrastructure() {
        if (cachedInfrastructure == null) {
            cachedInfrastructure = createTestInfrastructure();
        }
        return cachedInfrastructure;
    }

    /**
     * Shuts down the Axon configuration and releases infrastructure resources acquired during {@link #startApp()}.
     */
    @AfterEach
    void tearDown() {
        try {
            if (startedConfiguration != null) {
                startedConfiguration.shutdown();
            }
        } finally {
            try {
                if (cachedInfrastructure != null) {
                    cachedInfrastructure.stop();
                }
            } finally {
                cachedInfrastructure = null;
            }
        }
    }

    /**
     * Starts the Axon Framework application using the configured infrastructure and domain configurer.
     * <p>
     * Subclasses must call this method (directly or via {@code super.startApp()}) from a {@code @BeforeEach} method.
     * The infrastructure's {@link TestInfrastructure#start()} is invoked first (idempotent), followed by building and
     * starting the {@link AxonConfiguration}.
     */
    protected void startApp() {
        TestInfrastructure infra = testInfrastructure();
        infra.start();
        startedConfiguration = createConfigurer()
                .componentRegistry(infra::configureInfrastructure)
                .start();
        commandGateway = startedConfiguration.getComponent(CommandGateway.class);
    }

    /**
     * Purges persisted data via the current {@link TestInfrastructure}. Opt-in — only tests that require a clean
     * initial state (e.g. event-replay tests) should call this method.
     */
    protected void purgeData() {
        testInfrastructure().purgeData();
    }

    /**
     * Creates a unique ID string with the given {@code prefix}, suitable for use as an aggregate or entity identifier
     * in tests.
     *
     * @param prefix a human-readable prefix for the generated ID
     * @return a unique identifier string
     */
    protected static String createId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}

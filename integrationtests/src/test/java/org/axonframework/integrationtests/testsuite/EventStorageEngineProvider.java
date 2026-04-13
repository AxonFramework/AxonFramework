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

import org.axonframework.common.configuration.ComponentRegistry;

/**
 * SPI for pluggable event storage backends in integration tests. Implementations start a backend (e.g., a
 * Testcontainers container), register its components into the {@link ComponentRegistry} before each test, and optionally
 * reset persistent state between tests.
 * <p>
 * Implementations are started exactly once per JVM run via the JUnit 6 root store in
 * {@link EventStorageEngineExtension}, and closed automatically at root context teardown via {@link AutoCloseable}.
 *
 * @see EventStorageEngineExtension
 * @see TestEventStorageEngine
 * @see InMemoryEventStorageEngineProvider
 */
public interface EventStorageEngineProvider extends AutoCloseable {

    /**
     * Called by {@link EventStorageEngineExtension} exactly once per provider type per JVM run (via the JUnit 6 root
     * store). Start containers, create schemas, or perform any one-time setup here.
     */
    default void start() {
    }

    /**
     * Registers backend components into the {@link ComponentRegistry} before each test's {@code startApp()} call.
     * Implementations should register the necessary components (e.g., {@code EventStorageEngine}, {@code DataSource},
     * or {@code AxonServerConfiguration}) and disable conflicting enhancers as needed.
     *
     * @param cr the component registry to configure
     */
    void configure(ComponentRegistry cr);

    /**
     * Resets persistent state between tests (e.g., purge Axon Server context, truncate DB tables). Called explicitly by
     * tests that need a clean event store via {@link AbstractIntegrationTest#purgeEventStorage()}. No-op for InMemory
     * — config restart on each {@code startApp()} call gives a fresh engine automatically.
     */
    default void reset() {
    }

    /**
     * Called by the JUnit 6 root store teardown at JVM exit. Stop containers and release resources. No-op by default —
     * override to clean up backend resources.
     */
    @Override
    default void close() {
    }
}

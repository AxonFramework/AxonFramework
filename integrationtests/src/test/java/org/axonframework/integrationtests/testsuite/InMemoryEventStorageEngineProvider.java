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
 * Default {@link EventStorageEngineProvider} that uses the framework's built-in in-memory event storage engine. No
 * containers or external services are required.
 * <p>
 * Disables the {@code AxonServerConfigurationEnhancer} to prevent it from attempting to connect to Axon Server when the
 * axon-server-connector jar is present on the classpath. The framework then falls back to its default
 * {@code InMemoryEventStorageEngine} automatically.
 * <p>
 * {@link #reset()} and {@link #close()} are no-ops: each {@code startApp()} call recreates the application context
 * with a fresh in-memory engine.
 */
public class InMemoryEventStorageEngineProvider implements EventStorageEngineProvider {

    @Override
    public void configure(ComponentRegistry cr) {
        // Prevent the Axon Server enhancer from auto-connecting when axon-server-connector is on the classpath.
        // The framework defaults to InMemoryEventStorageEngine when no other engine is registered.
        cr.disableEnhancer("org.axonframework.axonserver.connector.AxonServerConfigurationEnhancer");
    }
}

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

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.Optional;

/**
 * JUnit 6 extension that manages the lifecycle of {@link EventStorageEngineProvider} instances for integration tests.
 * <p>
 * Each provider type is started exactly once per JVM run via the JUnit 6 root store's
 * {@link ExtensionContext.Store#computeIfAbsent} — the official JUnit 6 pattern for cross-class resource sharing.
 * JUnit calls {@link AutoCloseable#close()} on stored values deterministically at root context teardown.
 * <p>
 * The provider is resolved in this order:
 * <ol>
 *   <li>An explicit {@link TestEventStorageEngine#value()} on the test class or any superclass.</li>
 *   <li>System property {@code axon.test.storage-engine-provider}.</li>
 *   <li>Default: {@link InMemoryEventStorageEngineProvider}.</li>
 * </ol>
 * <p>
 * No {@code AfterEachCallback} is registered — {@link EventStorageEngineProvider#reset()} is <em>not</em> called
 * automatically. Tests that need a clean event store call {@link AbstractIntegrationTest#purgeEventStorage()}
 * explicitly.
 *
 * @see EventStorageEngineProvider
 * @see TestEventStorageEngine
 * @see AbstractIntegrationTest
 */
public class EventStorageEngineExtension implements BeforeAllCallback, TestInstancePostProcessor {

    private static final Namespace NS = Namespace.create(EventStorageEngineExtension.class);

    @Override
    public void beforeAll(ExtensionContext ctx) {
        Class<? extends EventStorageEngineProvider> cls = resolveProviderClass(ctx);
        // Official JUnit 6 pattern: computeIfAbsent on root store → starts exactly once per type per JVM run.
        // AutoCloseable.close() is called automatically at root context teardown (default in JUnit 6).
        ctx.getRoot().getStore(NS).computeIfAbsent(cls, k -> {
            try {
                EventStorageEngineProvider provider = cls.getDeclaredConstructor().newInstance();
                provider.start();
                return provider;
            } catch (Exception e) {
                throw new RuntimeException("Failed to start EventStorageEngineProvider: " + cls, e);
            }
        }, EventStorageEngineProvider.class);
    }

    @Override
    public void postProcessTestInstance(Object instance, ExtensionContext ctx) {
        if (instance instanceof AbstractIntegrationTest test) {
            test.storageEngineProvider = getProvider(ctx);
        }
    }

    private EventStorageEngineProvider getProvider(ExtensionContext ctx) {
        return ctx.getRoot().getStore(NS)
                  .get(resolveProviderClass(ctx), EventStorageEngineProvider.class);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends EventStorageEngineProvider> resolveProviderClass(ExtensionContext ctx) {
        // 1. Explicit @TestEventStorageEngine value — overrides everything including the system property
        Optional<TestEventStorageEngine> annotation = AnnotationSupport
                .findAnnotation(ctx.getRequiredTestClass(), TestEventStorageEngine.class);
        if (annotation.isPresent() && annotation.get().value() != EventStorageEngineProvider.class) {
            return annotation.get().value();
        }

        // 2. System property — CI default for tests without an explicit annotation value
        String prop = System.getProperty("axon.test.storage-engine-provider");
        if (prop != null) {
            try {
                return (Class<? extends EventStorageEngineProvider>) Class.forName(prop);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("EventStorageEngineProvider class not found: " + prop, e);
            }
        }

        // 3. Default: InMemory
        return InMemoryEventStorageEngineProvider.class;
    }
}

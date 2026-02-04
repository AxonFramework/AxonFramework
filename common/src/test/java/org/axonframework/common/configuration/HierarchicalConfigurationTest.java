/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.common.configuration;

import org.axonframework.common.util.StubLifecycleRegistry;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HierarchicalConfigurationTest {

    @Test
    void childLifecycleHandlersReceiveModuleConfigurationInsteadOfParent() {
        var parentConfiguration = mock(Configuration.class);
        var childConfiguration = mock(Configuration.class);
        var lifecycleRegistry = new StubLifecycleRegistry();
        var startHandlerCalled = new AtomicBoolean();
        var shutdownHandlerCalled = new AtomicBoolean();
        Configuration configuration = HierarchicalConfiguration.build(
                lifecycleRegistry,
                (childLifecycleRegistry) -> {
                    assertNotSame(lifecycleRegistry, childLifecycleRegistry);
                    childLifecycleRegistry.onStart(42, (c) -> {
                        assertSame(childConfiguration, c);
                        startHandlerCalled.set(true);
                    });
                    childLifecycleRegistry.onShutdown(42, (c) -> {
                        assertSame(childConfiguration, c);
                        shutdownHandlerCalled.set(true);
                    });
                    return childConfiguration;
                }
        );
        assertSame(childConfiguration, configuration);

        assertEquals(1, lifecycleRegistry.getStartHandlers().size());
        assertEquals(1, lifecycleRegistry.getStartHandlers().get(42).size());
        lifecycleRegistry.getStartHandlers().get(42).getFirst().run(parentConfiguration);
        assertTrue(startHandlerCalled.get());

        assertEquals(1, lifecycleRegistry.getShutdownHandlers().size());
        assertEquals(1, lifecycleRegistry.getShutdownHandlers().get(42).size());
        lifecycleRegistry.getShutdownHandlers().get(42).getFirst().run(parentConfiguration);
        assertTrue(shutdownHandlerCalled.get());
    }

    @Test
    void propagatesExceptionInLifecycleHandler() {
        var parentConfiguration = mock(Configuration.class);
        var lifecycleRegistry = new StubLifecycleRegistry();
        Configuration configuration = HierarchicalConfiguration.build(
                lifecycleRegistry,
                (childLifecycleRegistry) -> {
                    childLifecycleRegistry.onStart(42, (Consumer<Configuration>) (c) -> {
                        throw new RuntimeException("Expected exception");
                    });
                    return parentConfiguration;
                }
        );
        var result = lifecycleRegistry.getStartHandlers().get(42).getFirst().run(parentConfiguration);
        assertTrue(result.isCompletedExceptionally());
        assertInstanceOf(RuntimeException.class, result.exceptionNow());
        assertSame("Expected exception", result.exceptionNow().getMessage());

    }
}
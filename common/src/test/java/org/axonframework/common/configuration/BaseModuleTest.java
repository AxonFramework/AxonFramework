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

package org.axonframework.common.configuration;

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test validating the {@link SimpleModule}.
 *
 * @author Steven van Beelen
 */
class BaseModuleTest {

    private SimpleModule testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new SimpleModule("simple-module");
    }

    @Test
    void simpleModuleDelegatesToComponentRegistry() {
        AtomicReference<ComponentRegistry> detected = new AtomicReference<>();
        testSubject.componentRegistry(detected::set);
        var registry = new StubLifecycleRegistry();
        var config = new DefaultComponentRegistry().build(registry);
        testSubject.build(config, registry);
        assertNotNull(detected.get());
    }

    @Test
    void throwsIllegalArgumentExceptionForEmptyNameString() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleModule(""));
    }

    @Test
    void throwsIllegalArgumentExceptionForNullNameString() {
        assertThrows(IllegalArgumentException.class, () -> new SimpleModule(null));
    }

    @Test
    void simpleModuleDoesNotAllowToRegisterComponentRegistryHooksAfterBuild() {
        AtomicReference<ComponentRegistry> detected = new AtomicReference<>();
        var registry = new StubLifecycleRegistry();
        var config = new DefaultComponentRegistry().build(registry);
        testSubject.build(config, registry);

        var exc = assertThrows(IllegalStateException.class, () -> testSubject.componentRegistry(detected::set));
        assertThat(exc.getMessage()).isEqualTo("Module has already been built.");
        assertNull(detected.get());
    }


    private static class SimpleModule extends BaseModule<SimpleModule> {

        public SimpleModule(String name) {
            super(name);
        }
    }
}
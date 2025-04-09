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

package org.axonframework.configuration;

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicReference;

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

    private static class SimpleModule extends BaseModule<SimpleModule> {

        public SimpleModule(String name) {
            super(name);
        }
    }
}
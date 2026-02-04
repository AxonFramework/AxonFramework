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

import org.junit.jupiter.api.*;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test validating the {@link InstantiatedComponentDefinition}.
 *
 * @author Allard Buijze
 */
class InstantiatedComponentDefinitionTest extends ComponentTestSuite<InstantiatedComponentDefinition<String>> {

    @Override
    InstantiatedComponentDefinition<String> createComponent(Component.Identifier<String> id,
                                                            String instance) {
        return new InstantiatedComponentDefinition<>(id, instance);
    }

    @Override
    InstantiatedComponentDefinition<String> createComponent(Component.Identifier<String> id,
                                                            ComponentBuilder<String> builder) {
        Assumptions.abort("InstantiatedComponentDefinition doesn't support creation with builder.");
        return null;
    }

    @Override
    void registerStartHandler(InstantiatedComponentDefinition<String> testSubject,
                              int phase,
                              BiConsumer<Configuration, String> handler) {
        testSubject.onStart(phase, handler);
    }

    @Override
    void registerShutdownHandler(InstantiatedComponentDefinition<String> testSubject,
                                 int phase,
                                 BiConsumer<Configuration, String> handler) {
        testSubject.onShutdown(phase, handler);
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new InstantiatedComponentDefinition<>(null, "testValue"));
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullInstance() {
        Component.Identifier<String> testId = new Component.Identifier<>(String.class, "value");
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new InstantiatedComponentDefinition<>(testId, null));
    }
}

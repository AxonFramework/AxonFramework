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

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test validating the {@link LazyInitializedComponentDefinition}.
 *
 * @author Allard Buijze
 */
class LazyInitializedComponentDefinitionTest
        extends ComponentTestSuite<LazyInitializedComponentDefinition<String, String>> {

    @Override
    LazyInitializedComponentDefinition<String, String> createComponent(Component.Identifier<String> id,
                                                                       String instance) {
        return createComponent(id, c -> instance);
    }

    @Override
    LazyInitializedComponentDefinition<String, String> createComponent(Component.Identifier<String> id,
                                                                       ComponentBuilder<String> builder) {
        return new LazyInitializedComponentDefinition<>(id, builder);
    }

    @Override
    void registerStartHandler(LazyInitializedComponentDefinition<String, String> testSubject,
                              int phase,
                              BiConsumer<Configuration, String> handler) {
        testSubject.onStart(phase, handler);
    }

    @Override
    void registerShutdownHandler(LazyInitializedComponentDefinition<String, String> testSubject,
                                 int phase,
                                 BiConsumer<Configuration, String> handler) {
        testSubject.onShutdown(phase, handler);
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new LazyInitializedComponentDefinition<>(null, c -> "testValue"));
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullComponentBuilder() {
        Component.Identifier<String> testId = new Component.Identifier<>(String.class, "value");
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new LazyInitializedComponentDefinition<>(testId, null));
    }
}
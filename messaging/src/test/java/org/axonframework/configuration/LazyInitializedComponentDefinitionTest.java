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

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class LazyInitializedComponentDefinitionTest
        extends ComponentTestSuite<LazyInitializedComponentDefinition<String, String>> {

    @Override
    LazyInitializedComponentDefinition<String, String> createComponent(Component.Identifier<String> componentIdentifier,
                                                                       String instance) {
        return createComponent(componentIdentifier, c -> instance);
    }

    @Override
    LazyInitializedComponentDefinition<String, String> createComponent(Component.Identifier<String> componentIdentifier,
                                                                       ComponentFactory<String> factory) {
        return new LazyInitializedComponentDefinition<>(componentIdentifier, factory);
    }

    @Override
    void registerStartHandler(LazyInitializedComponentDefinition<String, String> testSubject, int phase,
                              BiConsumer<NewConfiguration, String> handler) {
        testSubject.onStart(phase, handler);
    }

    @Override
    void registerShutdownHandler(LazyInitializedComponentDefinition<String, String> testSubject, int phase,
                                 BiConsumer<NewConfiguration, String> handler) {
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
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new LazyInitializedComponentDefinition<>(new Component.Identifier<>(String.class, "value"),
                                                                    null));
    }
}
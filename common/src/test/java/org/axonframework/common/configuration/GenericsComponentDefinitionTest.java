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

import org.axonframework.common.TypeReference;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.ComponentDefinition;
import org.axonframework.common.configuration.Configuration;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenericsComponentDefinitionTest {

    @Test
    void canCreateComponentDefinitionWithGenericsWithBuilder() {
        MyGenericComponent<String> componentToBuild = new MyGenericComponent<>("testValue");
        ComponentDefinition<MyGenericComponent<String>> definition = ComponentDefinition.ofTypeAndName(
                new TypeReference<MyGenericComponent<String>>() {},
                "myGenericComponent"
        ).withBuilder(config -> componentToBuild);

        assertNotNull(definition);
        //noinspection rawtypes
        ComponentDefinition.ComponentCreator creator = (ComponentDefinition.ComponentCreator) definition;

        Component<?> component = creator.createComponent();
        Object resolve = component.resolve(mock(Configuration.class));
        assertSame(componentToBuild, resolve);
    }

    @Test
    void canCreateComponentDefinitionWithGenericsWithInstance() {
        MyGenericComponent<Integer> componentToBuild = new MyGenericComponent<>(42);
        ComponentDefinition<MyGenericComponent<Integer>> definition = ComponentDefinition.ofTypeAndName(
                new TypeReference<MyGenericComponent<Integer>>() {},
                "myGenericComponent"
        ).withInstance(componentToBuild);

        assertNotNull(definition);
        //noinspection rawtypes
        ComponentDefinition.ComponentCreator creator = (ComponentDefinition.ComponentCreator) definition;

        Component<?> component = creator.createComponent();
        Object resolve = component.resolve(mock(Configuration.class));
        assertSame(componentToBuild, resolve);
    }


    private static final class MyGenericComponent<T> {
        private final T value;

        public MyGenericComponent(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }
    }
}
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

import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test validating the {@link DecoratedComponent}.
 *
 * @author Allard Buijze
 */
class DecoratedComponentTest extends ComponentTestSuite<DecoratedComponent<String, String>> {

    @Override
    DecoratedComponent<String, String> createComponent(Component.Identifier<String> id,
                                                       String instance) {
        return new DecoratedComponent<>(new InstantiatedComponentDefinition<>(id, instance),
                                        (config, name, delegate) -> delegate + "test",
                                        Collections.emptyList(),
                                        Collections.emptyList());
    }

    @Override
    DecoratedComponent<String, String> createComponent(Component.Identifier<String> id,
                                                       ComponentBuilder<String> builder) {
        return new DecoratedComponent<>(new LazyInitializedComponentDefinition<>(id, builder),
                                        (config, name, delegate) -> delegate + "test",
                                        Collections.emptyList(),
                                        Collections.emptyList());
    }

    @Override
    void registerStartHandler(DecoratedComponent<String, String> testSubject,
                              int phase,
                              BiConsumer<Configuration, String> handler) {
        testSubject.onStart(phase, handler);
    }

    @Override
    void registerShutdownHandler(DecoratedComponent<String, String> testSubject,
                                 int phase,
                                 BiConsumer<Configuration, String> handler) {
        testSubject.onShutdown(phase, handler);
    }

    @Test
    void delegateReusesPreviouslyCreatedInstance() {
        //noinspection unchecked
        ComponentBuilder<String> mock = mock();
        when(mock.build(any())).thenReturn(TEST_COMPONENT);
        Component<String> target = new LazyInitializedComponentDefinition<>(identifier, mock);

        Component<String> decorated = new DecoratedComponent<>(target,
                                                               (config, name, delegate) -> delegate + "a",
                                                               Collections.emptyList(),
                                                               Collections.emptyList());

        assertEquals(TEST_COMPONENT + "a", decorated.resolve(configuration));
        verify(mock, times(1)).build(any());

        assertEquals(TEST_COMPONENT + "a", decorated.resolve(configuration));
        verify(mock, times(1)).build(any());
    }

    @Test
    void initializationRegistersLifecycleHandlersOfDecoratedComponents() {
        LazyInitializedComponentDefinition<String, String> target =
                new LazyInitializedComponentDefinition<>(identifier, builder);
        target.onStart(10, (configuration1, component) -> {

        });

        Component<String> result = new DecoratedComponent<>(
                target,
                (config, name, delegate) -> delegate + "test",
                List.of(new AbstractComponent.HandlerRegistration<>(
                        42, (c, component) -> FutureUtils.emptyCompletedFuture()
                )),
                Collections.emptyList()
        );

        result.initLifecycle(configuration, lifecycleRegistry);

        //noinspection unchecked
        verify(lifecycleRegistry).onStart(eq(10), any(Supplier.class));
        //noinspection unchecked
        verify(lifecycleRegistry).onStart(eq(42), any(Supplier.class));
    }

    @Test
    @Override
    void describeToDescribesBuilderWhenInstantiated() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, builder);
        testSubject.resolve(configuration);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty("instance", (Object) (TEST_COMPONENT + "test"));
        verify(testDescriptor).describeProperty("initialized", false);
        verify(testDescriptor).describeProperty("instantiated", true);
    }

    @Override
    void describeToDescribesBuilderWhenUninstantiatedButInitialized() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, builder);

        testSubject.initLifecycle(configuration, lifecycleRegistry);
        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty(eq("decorator"), any(Object.class));
        verify(testDescriptor).describeProperty("initialized", false);
        verify(testDescriptor).describeProperty("instantiated", false);
    }

    @Override
    void describeToDescribesBuilderWhenUnresolved() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, builder);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty(eq("decorator"), any(Object.class));
        verify(testDescriptor).describeProperty("initialized", false);
        verify(testDescriptor).describeProperty("instantiated", false);
    }

    @Test
    @Override
    void describeToDescribesInstanceWhenInstantiated() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);

        // Instantiate the component by getting it.
        testSubject.resolve(configuration);
        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty("instance", (Object) (TEST_COMPONENT + "test"));
        verify(testDescriptor).describeProperty("initialized", false);
        verify(testDescriptor).describeProperty("instantiated", true);
    }

    @Test
    @Override
    void describeToDescribesInstanceWhenInstantiatedAndInitialized() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);

        // Initialize the component by getting it.
        testSubject.resolve(configuration);
        testSubject.initLifecycle(configuration, lifecycleRegistry);
        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty("instance", (Object) (TEST_COMPONENT + "test"));
        verify(testDescriptor).describeProperty("initialized", true);
        verify(testDescriptor).describeProperty("instantiated", true);
    }
}

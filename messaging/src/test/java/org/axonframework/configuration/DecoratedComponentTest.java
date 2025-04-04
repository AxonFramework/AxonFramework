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

public class DecoratedComponentTest extends ComponentTestSuite<DecoratedComponent<String, String>> {


    @Test
    void delegateReusesPreviouslyCreatedInstance() {
        //noinspection unchecked
        ComponentFactory<String> mock = mock();
        when(mock.build(any())).thenReturn(TEST_COMPONENT);
        Component<String> target = new LazyInitializedComponentDefinition<>(identifier, mock);

        Component<String> decorated = new DecoratedComponent<>(target,
                                                               (config, name, delegate) -> delegate + "a",
                                                               Collections.emptyList(),
                                                               Collections.emptyList());

        assertEquals(TEST_COMPONENT+"a", decorated.resolve(configuration));
        verify(mock, times(1)).build(any());

        assertEquals(TEST_COMPONENT + "a", decorated.resolve(configuration));
        verify(mock, times(1)).build(any());
    }

    @Test
    void initializationRegistersLifecycleHandlersOfDecoratedComponents() {
        LazyInitializedComponentDefinition<String, String> target = new LazyInitializedComponentDefinition<>(identifier,
                                                                                                             factory);
        target.onStart(10, (configuration1, component) -> {

        });


        Component<String> result = new DecoratedComponent<>(target,
                                                            (config, name, delegate) -> delegate + "test",
                                                            List.of(new AbstractComponent.HandlerRegistration<>(42,
                                                                                                                (c, component) -> FutureUtils.emptyCompletedFuture())),
                                                            Collections.emptyList());

        result.initLifecycle(configuration, lifecycleRegistry);

        verify(lifecycleRegistry).onStart(eq(10), any(Supplier.class));
        verify(lifecycleRegistry).onStart(eq(42), any(Supplier.class));
    }


    @Override
    DecoratedComponent<String, String> createComponent(Component.Identifier<String> componentIdentifier,
                                                       String instance) {
        return new DecoratedComponent<>(new InstantiatedComponentDefinition<>(componentIdentifier, instance),
                                        (config, name, delegate) -> delegate + "test",
                                        Collections.emptyList(),
                                        Collections.emptyList());
    }

    @Override
    DecoratedComponent<String, String> createComponent(Component.Identifier<String> componentIdentifier,
                                                       ComponentFactory<String> factory) {
        return new DecoratedComponent<>(new LazyInitializedComponentDefinition<>(componentIdentifier, factory),
                                        (config, name, delegate) -> delegate + "test",
                                        Collections.emptyList(),
                                        Collections.emptyList());
    }

    @Override
    void registerStartHandler(DecoratedComponent<String, String> testSubject, int phase,
                              BiConsumer<NewConfiguration, String> handler) {
        testSubject.onStart(phase, handler);
    }

    @Override
    void registerShutdownHandler(DecoratedComponent<String, String> testSubject, int phase,
                                 BiConsumer<NewConfiguration, String> handler) {
        testSubject.onShutdown(phase, handler);
    }

    @Test
    void describeToDescribesBuilderWhenInstantiated() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);
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

        Component<String> testSubject = createComponent(identifier, factory);

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

        Component<String> testSubject = createComponent(identifier, factory);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty(eq("decorator"), any(Object.class));
        verify(testDescriptor).describeProperty("initialized", false);
        verify(testDescriptor).describeProperty("instantiated", false);
    }

    @Test
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

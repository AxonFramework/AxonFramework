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

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Component.Identifier;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link Component} and {@link ComponentDefinition} implementations.
 *
 * @author Steven van Beelen
 */
abstract class ComponentTestSuite<D extends Component<String>> {

    protected static final String TEST_COMPONENT = "Hello World!";

    protected Identifier<String> identifier;
    protected ComponentFactory<String> factory;

    protected NewConfiguration configuration;
    protected LifecycleRegistry lifecycleRegistry;

    @BeforeEach
    void setUp() {
        identifier = new Identifier<>(String.class, "id");
        factory = c -> TEST_COMPONENT;

        configuration = mock(NewConfiguration.class);
        // wrap the mock to allow default methods to be taken into account
        lifecycleRegistry = mock();
    }

    abstract D createComponent(Component.Identifier<String> componentIdentifier, String instance);

    abstract D createComponent(Component.Identifier<String> componentIdentifier, ComponentFactory<String> factory);

    abstract void registerStartHandler(D testSubject, int phase, BiConsumer<NewConfiguration, String> handler);

    abstract void registerShutdownHandler(D testSubject, int phase, BiConsumer<NewConfiguration, String> handler);

    @Test
    void resolveThrowsNullPointerExceptionForNullConfiguration() {
        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.resolve(null));
    }

    @Test
    void resolveThrowsNullPointerExceptionForNullLifecycleRegistry() {
        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.initLifecycle(configuration, null));
    }

    @Test
    void componentReturnsItsIdentifier() {
        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);

        assertSame(identifier, testSubject.identifier());
    }

    @Test
    void resolveInvokesComponentBuilderMethodOnlyOnce() {
        AtomicInteger counter = new AtomicInteger();

        Component<String> testSubject = createComponent(identifier, c -> {
            counter.incrementAndGet();
            return factory.build(c);
        });

        assertEquals(0, counter.get());
        testSubject.resolve(configuration);
        assertEquals(1, counter.get());
        testSubject.resolve(configuration);
        assertEquals(1, counter.get());
    }

    @Test
    void isInstantiatedReturnsAsExpectedOnPreCreatedComponent() {
        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);

        testSubject.resolve(configuration);
        assertTrue(testSubject.isInstantiated());
    }

    @Test
    void isInstantiatedReturnsAsExpectedOnFactoryMethod() {
        Component<String> testSubject = createComponent(identifier, factory);

        assertFalse(testSubject.isInstantiated());
        testSubject.resolve(configuration);
        assertTrue(testSubject.isInstantiated());
    }

    @Test
    void isInitializedReturnsAsExpectedOnPreCreatedComponent() {
        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);

        assertFalse(testSubject.isInitialized());
        testSubject.initLifecycle(configuration, lifecycleRegistry);
        assertTrue(testSubject.isInitialized());
    }

    @Test
    void isInitializedReturnsAsExpectedOnFactoryMethod() {
        Component<String> testSubject = createComponent(identifier, factory);

        assertFalse(testSubject.isInitialized());
        testSubject.initLifecycle(configuration, lifecycleRegistry);
        assertTrue(testSubject.isInitialized());
    }

    @Test
    void identifierConstructorThrowsNullPointerExceptionForNullType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new Identifier<>(null, "id"));
    }

    @Test
    void identifierConstructorThrowsNullPointerExceptionForNullName() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new Identifier<>(String.class, null));
    }

    @Test
    void identifierConstructorThrowsIllegalArgumentExceptionForEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> new Identifier<>(String.class, ""));
    }

    @Test
    void initializationDoesNothingWhenAlreadyInitialized() {
        D testComponent = createComponent(identifier, factory);
        registerStartHandler(testComponent, 10, (cfg, cmp) -> {});

        testComponent.initLifecycle(configuration, lifecycleRegistry);
        verify(lifecycleRegistry).onStart(eq(10), any(Supplier.class));
        verifyNoMoreInteractions(lifecycleRegistry);
        reset(lifecycleRegistry);

        testComponent.initLifecycle(configuration, lifecycleRegistry);
        verifyNoInteractions(lifecycleRegistry);

    }

    @SuppressWarnings("unchecked")
    @Test
    void initializationRegistersStartupAndShutdownHandlers() {
        D testComponent = createComponent(identifier, TEST_COMPONENT);
        registerStartHandler(testComponent, 1, mock());
        registerStartHandler(testComponent, 10, mock());
        registerShutdownHandler(testComponent, 20, mock());
        registerShutdownHandler(testComponent, 42, mock());

        testComponent.initLifecycle(configuration, new LifecycleRegistry() {
            @Override
            public LifecycleRegistry registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit) {
                return lifecycleRegistry.registerLifecyclePhaseTimeout(timeout, timeUnit);
            }

            @Override
            public LifecycleRegistry onStart(int phase, @Nonnull LifecycleHandler startHandler) {
                return lifecycleRegistry.onStart(phase, startHandler);
            }

            @Override
            public LifecycleRegistry onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
                return lifecycleRegistry.onShutdown(phase, shutdownHandler);
            }
        });

        verify(lifecycleRegistry).onStart(eq(1), any(LifecycleHandler.class));
        verify(lifecycleRegistry).onStart(eq(10), any(LifecycleHandler.class));
        verify(lifecycleRegistry).onShutdown(eq(20), any(LifecycleHandler.class));
        verify(lifecycleRegistry).onShutdown(eq(42), any(LifecycleHandler.class));
    }

    @Test
    void describeToDescribesBuilderWhenUnresolved() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, factory);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty("factory", factory);
        verify(testDescriptor).describeProperty("initialized", false);
        verify(testDescriptor).describeProperty("instantiated", false);
    }

    @Test
    void describeToDescribesBuilderWhenInstantiated() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, TEST_COMPONENT);
        testSubject.resolve(configuration);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty("instance", (Object) TEST_COMPONENT);
        verify(testDescriptor).describeProperty("initialized", false);
        verify(testDescriptor).describeProperty("instantiated", true);
    }

    @Test
    void describeToDescribesBuilderWhenUninstantiatedButInitialized() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, factory);

        testSubject.initLifecycle(configuration, lifecycleRegistry);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty("factory", factory);
        verify(testDescriptor).describeProperty("initialized", true);
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
        verify(testDescriptor).describeProperty("instance", (Object) TEST_COMPONENT);
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
        verify(testDescriptor).describeProperty("instance", (Object) TEST_COMPONENT);
        verify(testDescriptor).describeProperty("initialized", true);
        verify(testDescriptor).describeProperty("instantiated", true);
    }
}
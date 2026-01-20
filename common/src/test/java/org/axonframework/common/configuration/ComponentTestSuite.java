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

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.common.configuration.*;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.configuration.Component.Identifier;
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
    protected ComponentBuilder<String> builder;

    protected Configuration configuration;
    protected LifecycleRegistry lifecycleRegistry;

    @BeforeEach
    void setUp() {
        identifier = new Identifier<>(String.class, "id");
        builder = c -> TEST_COMPONENT;

        configuration = mock(Configuration.class);
        // wrap the mock to allow default methods to be taken into account
        lifecycleRegistry = mock();
    }

    /**
     * Creates a component for the given {@code id} and containing the given {@code instance} for testing.
     *
     * @param id       The identifier of the component to create for the given {@code instance}.
     * @param instance The already instantiated instance to contain in a component.
     * @return The {@link Component} of type {@code D} for testing.
     */
    abstract D createComponent(Component.Identifier<String> id,
                               @SuppressWarnings("SameParameterValue") String instance);

    /**
     * Creates a component for the given {@code id} and with the given {@code builder} to construct the component's
     * instance for testing.
     *
     * @param id      The identifier of the component to create for the given {@code instance}.
     * @param builder The builder constructing the component's instance.
     * @return The {@link Component} of type {@code D} for testing.
     */
    abstract D createComponent(Component.Identifier<String> id, ComponentBuilder<String> builder);

    /**
     * Registers a start-up {@code handler} with the given {@code testSubject} in the specified {@code phase}.
     *
     * @param testSubject The test subject to register a start-up {@code handler} for.
     * @param phase       The phase to register the start-up {@code handler} in.
     * @param handler     The start-up handler to trigger in the specified {@code phase} for the given
     *                    {@code testSubject}.
     */
    abstract void registerStartHandler(D testSubject, int phase, BiConsumer<Configuration, String> handler);

    /**
     * Registers a shutdown {@code handler} with the given {@code testSubject} in the specified {@code phase}.
     *
     * @param testSubject The test subject to register a shutdown {@code handler} for.
     * @param phase       The phase to register the shutdown {@code handler} in.
     * @param handler     The shutdown handler to trigger in the specified {@code phase} for the given
     *                    {@code testSubject}.
     */
    abstract void registerShutdownHandler(D testSubject, int phase, BiConsumer<Configuration, String> handler);

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
            return builder.build(c);
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
        Component<String> testSubject = createComponent(identifier, builder);

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
        Component<String> testSubject = createComponent(identifier, builder);

        assertFalse(testSubject.isInitialized());
        testSubject.initLifecycle(configuration, lifecycleRegistry);
        assertTrue(testSubject.isInitialized());
    }

    @Test
    void identifierConstructorThrowsNullPointerExceptionForNullTypeReference() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new Identifier<>((TypeReference<?>) null, "id"));
    }

    @Test
    void identifierConstructorThrowsNullPointerExceptionForNullType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new Identifier<>((Class<?>) null, "id"));
    }

    @Test
    void identifierConstructorThrowsIllegalArgumentExceptionForEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> new Identifier<>(String.class, ""));
    }

    @Test
    void initializationDoesNothingWhenAlreadyInitialized() {
        D testComponent = createComponent(identifier, builder);
        registerStartHandler(testComponent, 10, (cfg, cmp) -> {
        });

        testComponent.initLifecycle(configuration, lifecycleRegistry);
        //noinspection unchecked
        verify(lifecycleRegistry).onStart(eq(10), any(Supplier.class));
        verifyNoMoreInteractions(lifecycleRegistry);
        reset(lifecycleRegistry);

        testComponent.initLifecycle(configuration, lifecycleRegistry);
        verifyNoInteractions(lifecycleRegistry);
    }

    @Test
    void initializationRegistersStartupAndShutdownHandlers() {
        D testComponent = createComponent(identifier, TEST_COMPONENT);
        //noinspection unchecked
        registerStartHandler(testComponent, 1, mock());
        //noinspection unchecked
        registerStartHandler(testComponent, 10, mock());
        //noinspection unchecked
        registerShutdownHandler(testComponent, 20, mock());
        //noinspection unchecked
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

        Component<String> testSubject = createComponent(identifier, builder);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty("builder", builder);
        verify(testDescriptor).describeProperty("initialized", false);
        verify(testDescriptor).describeProperty("instantiated", false);
    }

    @Test
    void describeToDescribesBuilderWhenInstantiated() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

        Component<String> testSubject = createComponent(identifier, builder);
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

        Component<String> testSubject = createComponent(identifier, builder);

        testSubject.initLifecycle(configuration, lifecycleRegistry);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier);
        verify(testDescriptor).describeProperty("builder", builder);
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
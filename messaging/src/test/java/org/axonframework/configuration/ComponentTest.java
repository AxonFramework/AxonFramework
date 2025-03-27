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

import org.axonframework.configuration.Component.Identifier;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link Component}.
 *
 * @author Steven van Beelen
 */
class ComponentTest {

    private static final String TEST_COMPONENT = "Hello World!";

    private Identifier<String> identifier;
    private ComponentFactory<String> factory;

    private NewConfiguration configuration;
    private LifecycleRegistry lifecycleRegistry;

    @BeforeEach
    void setUp() {
        identifier = new Identifier<>(String.class, "id");
        factory = c -> TEST_COMPONENT;

        configuration = mock(NewConfiguration.class);
        lifecycleRegistry = mock(LifecycleRegistry.class);
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new Component<>(null, factory));
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new Component<>(identifier, null));
    }

    @Test
    void resolveThrowsNullPointerExceptionForNullConfiguration() {
        Component<String> testSubject = new Component<>(identifier, factory);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.resolve(null));
    }

    @Test
    void resolveThrowsNullPointerExceptionForNullLifecycleRegistry() {
        Component<String> testSubject = new Component<>(identifier, factory);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.initLifecycle(configuration, null));
    }

    @Test
    void componentReturnsItsIdentifier() {
        Component<String> testSubject = new Component<>(identifier, factory);

        assertSame(identifier, testSubject.identifier());
    }

    @Test
    void resolveInvokesComponentBuilderMethodOnlyOnce() {
        AtomicInteger counter = new AtomicInteger();

        Component<String> testSubject = new Component<>(identifier, c -> {
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
    void decorateInvokesDecoratorsAtGivenOrder() {
        Component<String> testSubject = new Component<>(identifier, factory);
        Component<String> result1 = testSubject.decorate((c, name, delegate) -> delegate + "a");
        Component<String> result2 = result1.decorate((c, name, delegate) -> delegate + "b");
        Component<String> result3 = result2.decorate((c, name, delegate) -> delegate + "c");

        assertEquals("Hello World!a", result1.resolve(configuration));
        assertEquals("Hello World!ab", result2.resolve(configuration));
        assertEquals("Hello World!abc", result3.resolve(configuration));
    }

    @Test
    void delegateReusesPreviouslyCreatedInstance() {
        //noinspection unchecked
        ComponentFactory<String> mock = mock();
        when(mock.build(any())).thenReturn(TEST_COMPONENT);
        Component<String> testSubject = new Component<>(identifier, mock);

        Component<String> decorated = testSubject.decorate((c, name, delegate) -> delegate + "a");

        assertEquals(TEST_COMPONENT, testSubject.resolve(configuration));
        verify(mock, times(1)).build(any());

        assertEquals(TEST_COMPONENT + "a", decorated.resolve(configuration));
        verify(mock, times(1)).build(any());
    }

    @Test
    void isResolvedReturnsAsExpected() {
        Component<String> testSubject = new Component<>(identifier, factory,
                                                        (lifecycleRegistry1, stringSupplier) -> {
                                                        });

        assertFalse(testSubject.isResolved());
        testSubject.resolve(configuration);
        assertTrue(testSubject.isResolved());
    }

    @Test
    void isInitializedReturnsAsExpected() {
        Component<String> testSubject = new Component<>(identifier, factory);

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
    void initializationRegistersLifecycleHandlersOfDecoratedComponents() {
        List<String> decoratorCalls = new ArrayList<>();
        Component<CharSequence> testComponent = new Component<>(new Identifier<>(CharSequence.class, "id"), factory,
                                                                (lcr, component) -> decoratorCalls.add(component.get()));

        Component<CharSequence> result = testComponent.decorate((config, name, component) -> component + "test",
                                                                (lcr, component) -> decoratorCalls.add(component.get()));

        result.initLifecycle(configuration, lifecycleRegistry);

        assertEquals(List.of("Hello World!", "Hello World!test"), decoratorCalls);
    }

    @SuppressWarnings("unchecked")
    @Test
    void initializationDoesNothingWhenAlreadyInitialized() {
        BiConsumer<LifecycleRegistry, Supplier<String>> mock = mock();
        Component<CharSequence> testComponent = new Component<>(new Identifier<>(CharSequence.class, "id"), factory, mock);

        testComponent.initLifecycle(configuration, lifecycleRegistry);
        reset(mock);

        testComponent.initLifecycle(configuration, lifecycleRegistry);
        verifyNoInteractions(mock);
    }

}
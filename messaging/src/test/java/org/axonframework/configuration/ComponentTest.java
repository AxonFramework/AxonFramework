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

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Component.Identifier;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    private LifecycleSupportingConfiguration config;
    private ComponentBuilder<String> builder;

    @BeforeEach
    void setUp() {
        identifier = new Identifier<>(String.class, "id");
        config = mock(LifecycleSupportingConfiguration.class);
        builder = c -> TEST_COMPONENT;
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new Component<>(null, () -> config, builder));
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullConfiguration() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new Component<>(identifier, (LifecycleSupportingConfiguration) null, builder));
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullConfigurationSupplier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new Component<>(identifier, (Supplier<LifecycleSupportingConfiguration>) null, builder));
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new Component<>(identifier, () -> config, null));
    }

    @Test
    void getInvokesComponentBuilderMethodOnlyOnce() {
        AtomicInteger counter = new AtomicInteger();

        Component<String> testSubject = new Component<>(identifier, config, c -> {
            counter.incrementAndGet();
            return builder.build(c);
        });

        assertEquals(0, counter.get());
        testSubject.get();
        assertEquals(1, counter.get());
        testSubject.get();
        assertEquals(1, counter.get());
    }

    @Test
    void decorateInvokesDecoratorsAtGivenOrder() {
        String expectedComponent = TEST_COMPONENT + "cba";

        Component<String> testSubject = new Component<>(identifier, config, builder);
        testSubject.decorate((c, delegate) -> delegate + "a", 2)
                   .decorate((c, delegate) -> delegate + "b", 1)
                   .decorate((c, delegate) -> delegate + "c", 0);

        assertEquals(expectedComponent, testSubject.get());
    }

    @Test
    void decorateReplacesPreviousDecoratorsForReusedOrder() {
        int testOrder = 0;
        String replacedDecoration = "this-will-not-be-there-on-creation";
        String keptDecoration = "and-this-will-be";

        Component<String> testSubject = new Component<>(identifier, config, builder);
        testSubject.decorate((c, delegate) -> delegate + replacedDecoration, testOrder)
                   .decorate((c, delegate) -> delegate + keptDecoration, testOrder);

        assertNotEquals(TEST_COMPONENT + replacedDecoration, testSubject.get());
        assertEquals(TEST_COMPONENT + keptDecoration, testSubject.get());
    }

    @Test
    void isInitializedReturnsAsExpected() {
        Component<String> testSubject = new Component<>(identifier, config, builder);

        assertFalse(testSubject.isInitialized());
        testSubject.get();
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
    void describeToDescribesBuilderWhenUninitialized() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);
        ComponentDecorator<String> testDecorator = (config, delegate) -> delegate;

        Component<String> testSubject = new Component<>(identifier, config, builder)
                .decorate(testDecorator, 1);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier.toString());
        verify(testDescriptor).describeProperty("builder", builder);
        verify(testDescriptor).describeProperty("decorators", Map.of(1, testDecorator));
        verify(testDescriptor).describeProperty("initialized", false);
    }

    @Test
    void describeToDescribesInstanceWhenInitialized() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);
        ComponentDecorator<String> testDecorator = (config, delegate) -> delegate;

        Component<String> testSubject = new Component<>(identifier, config, builder)
                .decorate(testDecorator, 1);

        // Initialize the component by getting it.
        testSubject.get();
        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("identifier", identifier.toString());
        verify(testDescriptor).describeProperty("component", (Object) TEST_COMPONENT);
        verify(testDescriptor).describeProperty("initialized", true);
    }
}
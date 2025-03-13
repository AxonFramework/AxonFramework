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

import java.util.concurrent.atomic.AtomicInteger;

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
    private LifecycleRegistry<?> lifecycleRegistry;

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
    void getThrowsNullPointerExceptionForNullConfiguration() {
        Component<String> testSubject = new Component<>(identifier, factory);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.get(null, lifecycleRegistry));
    }

    @Test
    void getThrowsNullPointerExceptionForNullLifecycleRegistry() {
        Component<String> testSubject = new Component<>(identifier, factory);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.get(configuration, null));
    }

    @Test
    void getInvokesComponentBuilderMethodOnlyOnce() {
        AtomicInteger counter = new AtomicInteger();

        Component<String> testSubject = new Component<>(identifier, c -> {
            counter.incrementAndGet();
            return factory.build(c);
        });

        assertEquals(0, counter.get());
        testSubject.get(configuration, lifecycleRegistry);
        assertEquals(1, counter.get());
        testSubject.get(configuration, lifecycleRegistry);
        assertEquals(1, counter.get());
    }

    @Test
    void decorateInvokesDecoratorsAtGivenOrder() {
        String expectedComponent = TEST_COMPONENT + "cba";

        Component<String> testSubject = new Component<>(identifier, factory);
        testSubject.decorate((c, delegate) -> delegate + "a", 2)
                   .decorate((c, delegate) -> delegate + "b", 1)
                   .decorate((c, delegate) -> delegate + "c", 0);

        assertEquals(expectedComponent, testSubject.get(configuration, lifecycleRegistry));
    }

    @Test
    void decorateReplacesPreviousDecoratorsForReusedOrder() {
        int testOrder = 0;
        String replacedDecoration = "this-will-not-be-there-on-creation";
        String keptDecoration = "and-this-will-be";

        Component<String> testSubject = new Component<>(identifier, factory);
        testSubject.decorate((c, delegate) -> delegate + replacedDecoration, testOrder)
                   .decorate((c, delegate) -> delegate + keptDecoration, testOrder);

        assertNotEquals(TEST_COMPONENT + replacedDecoration, testSubject.get(configuration, lifecycleRegistry));
        assertEquals(TEST_COMPONENT + keptDecoration, testSubject.get(configuration, lifecycleRegistry));
    }

    @Test
    void isInitializedReturnsAsExpected() {
        Component<String> testSubject = new Component<>(identifier, factory);

        assertFalse(testSubject.isInitialized());
        testSubject.get(configuration, lifecycleRegistry);
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
}
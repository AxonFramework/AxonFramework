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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link Components}.
 *
 * @author Steven van Beelen
 */
class ComponentsTest {

    private static final Identifier<String> IDENTIFIER = new Identifier<>(String.class, "id");

    private LifecycleSupportingConfiguration config;

    private Components testSubject;

    @BeforeEach
    void setUp() {
        config = mock(LifecycleSupportingConfiguration.class);

        testSubject = new Components();
    }

    @Test
    void getThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.get(null));
    }

    @Test
    void getReturnsEmpty() {
        assertTrue(testSubject.get(IDENTIFIER).isEmpty());
    }

    @Test
    void getReturnsPutComponent() {
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");

        testSubject.put(IDENTIFIER, testComponent);

        Optional<Component<String>> result = testSubject.get(IDENTIFIER);
        assertTrue(result.isPresent());
        assertEquals(testComponent, result.get());
    }

    @Test
    void getUnwrappedThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.getUnwrapped(null));
    }

    @Test
    void getUnwrappedReturnsEmpty() {
        assertTrue(testSubject.getUnwrapped(IDENTIFIER).isEmpty());
    }

    @Test
    void getUnwrappedReturnsPutComponent() {
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");

        testSubject.put(IDENTIFIER, testComponent);

        Optional<String> result = testSubject.getUnwrapped(IDENTIFIER);
        assertTrue(result.isPresent());
        assertEquals(testComponent.get(), result.get());
    }

    @Test
    void computeIfAbsentDoesNotComputeIfIdentifierIsAlreadyPresent() {
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");
        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.put(IDENTIFIER, testComponent);
        testSubject.computeIfAbsent(IDENTIFIER, id -> {
            invoked.set(true);
            return mock(Component.class);
        });


        assertFalse(invoked.get());
        Optional<String> optionalResult = testSubject.getUnwrapped(IDENTIFIER);
        assertTrue(optionalResult.isPresent());
        assertEquals(testComponent.get(), optionalResult.get());
    }

    @Test
    void computeIfAbsentComputesForAbsentIdentifier() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");

        testSubject.computeIfAbsent(IDENTIFIER, id -> {
            invoked.set(true);
            return testComponent;
        });

        assertTrue(invoked.get());
        Optional<String> optionalResult = testSubject.getUnwrapped(IDENTIFIER);
        assertTrue(optionalResult.isPresent());
        assertEquals(testComponent.get(), optionalResult.get());
    }

    @Test
    void containsReturnsAsExpected() {
        Identifier<Integer> unknownIdentifier = new Identifier<>(Integer.class, "some-unknown-id");
        testSubject.put(IDENTIFIER, new Component<>(IDENTIFIER, config, c -> "some-state"));

        assertTrue(testSubject.contains(IDENTIFIER));
        assertFalse(testSubject.contains(unknownIdentifier));
    }

    @Test
    void identifiersReturnsAllRegisteredComponentsTheirIdentifiers() {
        assertTrue(testSubject.identifiers().isEmpty());

        testSubject.put(IDENTIFIER, new Component<>(IDENTIFIER, config, c -> "some-state"));

        Set<Identifier<?>> result = testSubject.identifiers();
        assertFalse(result.isEmpty());
        assertTrue(result.contains(IDENTIFIER));
    }

    @Test
    void replaceDoesNothingIfThereIsNoComponentToReplaceForTheGivenIdentifier() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        boolean result = testSubject.replace(IDENTIFIER, old -> {
            invoked.set(true);
            return new Component<>(IDENTIFIER, config, c -> "replacement");
        });

        assertFalse(invoked.get());
        assertFalse(result);
    }

    @Test
    void replaceReplacesComponents() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.put(IDENTIFIER, new Component<>(IDENTIFIER, config, c -> "some-state"));

        boolean result = testSubject.replace(IDENTIFIER, old -> {
            invoked.set(true);
            return new Component<>(IDENTIFIER, config, c -> "replacement");
        });

        assertTrue(invoked.get());
        assertTrue(result);
        Optional<String> resultComponent = testSubject.getUnwrapped(IDENTIFIER);
        assertTrue(resultComponent.isPresent());
        assertEquals("replacement", resultComponent.get());
    }

    @Test
    void describeToDescribesComponents() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");
        testSubject.put(IDENTIFIER, testComponent);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("components", Map.of(IDENTIFIER, testComponent));
    }
}
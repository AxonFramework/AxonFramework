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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link Components}.
 *
 * @author Steven van Beelen
 */
class ComponentsTest {

    private static final Component.Identifier<String> IDENTIFIER = new Component.Identifier<>(String.class, "id");

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
    void getThrowsNullPointerExceptionForUnknownIdentifier() {
        assertThrows(NullPointerException.class, () -> testSubject.get(IDENTIFIER));
    }

    @Test
    void getReturnsPutComponent() {
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");

        testSubject.put(IDENTIFIER, testComponent);

        assertEquals(testComponent.get(), testSubject.get(IDENTIFIER));
    }

    @Test
    void getOptionalThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.getOptional(null));
    }

    @Test
    void getOptionalReturnsEmptyOptional() {
        assertTrue(testSubject.getOptional(IDENTIFIER).isEmpty());
    }

    @Test
    void getOptionalReturnsPutComponent() {
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");

        testSubject.put(IDENTIFIER, testComponent);

        Optional<String> result = testSubject.getOptional(IDENTIFIER);
        assertTrue(result.isPresent());
        assertEquals(testComponent.get(), result.get());
    }

    @Test
    void getComponentThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.getComponent(null));
    }

    @Test
    void getComponentThrowsNullPointerExceptionForUnknownIdentifier() {
        assertThrows(NullPointerException.class, () -> testSubject.getComponent(IDENTIFIER));
    }

    @Test
    void getComponentReturnsPutComponent() {
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");

        testSubject.put(IDENTIFIER, testComponent);

        assertEquals(testComponent, testSubject.getComponent(IDENTIFIER));
    }

    @Test
    void getOptionalComponentThrowsNullPointerExceptionForNullIdentifier() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.getOptionalComponent(null));
    }

    @Test
    void getOptionalComponentReturnsEmptyOptional() {
        assertTrue(testSubject.getOptionalComponent(IDENTIFIER).isEmpty());
    }

    @Test
    void getOptionalComponentReturnsPutComponent() {
        Component<String> testComponent = new Component<>(IDENTIFIER, config, c -> "some-state");

        testSubject.put(IDENTIFIER, testComponent);

        Optional<Component<String>> result = testSubject.getOptionalComponent(IDENTIFIER);
        assertTrue(result.isPresent());
        assertEquals(testComponent, result.get());
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
        assertEquals(testComponent.get(), testSubject.get(IDENTIFIER));
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
        assertEquals(testComponent.get(), testSubject.get(IDENTIFIER));
    }
}
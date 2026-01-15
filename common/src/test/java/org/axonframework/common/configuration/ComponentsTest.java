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

import org.axonframework.common.configuration.Component.Identifier;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.util.MockException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.util.ArrayList;
import java.util.List;
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
@ExtendWith(MockitoExtension.class)
class ComponentsTest {

    private static final Identifier<String> IDENTIFIER = new Identifier<>(String.class, "id");

    private Components testSubject;

    @BeforeEach
    void setUp() {
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
        Component<String> testComponent = new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state");

        testSubject.put(testComponent);

        Optional<Component<String>> result = testSubject.get(IDENTIFIER);
        assertTrue(result.isPresent());
        assertEquals(testComponent, result.get());
    }

    @Test
    void getReturnsPutComponentWhenComponentTypeIsAssignableToGivenIdType() {
        Component<String> testComponent = new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state");
        Identifier<Object> testId = new Identifier<>(Object.class, "id");

        testSubject.put(testComponent);

        Optional<Component<Object>> result = testSubject.get(testId);
        assertTrue(result.isPresent());
        assertEquals(testComponent, result.get());
    }

    @Test
    void getThrowsAmbiguousComponentMatchExceptionWhenMultipleComponentsAreAssignableToGivenIdType() {
        Component<String> stringTestComponent = new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state");
        Component<Integer> integerTestComponent =
                new InstantiatedComponentDefinition<>(new Identifier<>(Integer.class, "id"), 42);
        Identifier<Object> testId = new Identifier<>(Object.class, "id");

        testSubject.put(stringTestComponent);
        testSubject.put(integerTestComponent);

        assertThrows(AmbiguousComponentMatchException.class, () -> testSubject.get(testId));
    }

    @Test
    void computeIfAbsentDoesNotComputeIfIdentifierIsAlreadyPresent(@Mock Component<String> newComponent) {
        Component<String> testComponent = new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state");
        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.put(testComponent);
        testSubject.computeIfAbsent(IDENTIFIER, () -> {
            invoked.set(true);
            return newComponent;
        });


        assertFalse(invoked.get());
        Optional<Component<String>> optionalResult = testSubject.get(IDENTIFIER);
        assertTrue(optionalResult.isPresent());
        assertEquals(testComponent, optionalResult.get());
    }

    @Test
    void computeIfAbsentComputesForAbsentIdentifier() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Component<String> testComponent = new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state");

        testSubject.computeIfAbsent(IDENTIFIER, () -> {
            invoked.set(true);
            return testComponent;
        });

        assertTrue(invoked.get());
        Optional<Component<String>> optionalResult = testSubject.get(IDENTIFIER);
        assertTrue(optionalResult.isPresent());
        assertEquals(testComponent, optionalResult.get());
    }

    @Test
    void replacingAComponentRemovesThePreviousOne() {
        Component<String> original = new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state");
        Component<String> replacement = new InstantiatedComponentDefinition<>(IDENTIFIER, "other-state");

        testSubject.put(original);
        testSubject.replace(IDENTIFIER, c -> replacement);

        assertTrue(testSubject.contains(IDENTIFIER));
        assertTrue(testSubject.get(IDENTIFIER).isPresent());
        assertSame(replacement, testSubject.get(IDENTIFIER).get());
    }

    @Test
    void replacingNonExistentComponentDoesntRegisterIt() {
        List<Component<?>> components = new ArrayList<>();
        testSubject.replace(IDENTIFIER, c -> {
            components.add(c);
            return c;
        });

        assertTrue(components.isEmpty());
    }

    @Test
    void containsReturnsAsExpected() {
        Identifier<Integer> unknownIdentifier = new Identifier<>(Integer.class, "some-unknown-id");
        testSubject.put(new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state"));

        assertTrue(testSubject.contains(IDENTIFIER));
        assertFalse(testSubject.contains(unknownIdentifier));
    }

    @Test
    void identifiersReturnsAllRegisteredComponentsTheirIdentifiers() {
        assertTrue(testSubject.identifiers().isEmpty());

        testSubject.put(new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state"));

        Set<Identifier<?>> result = testSubject.identifiers();
        assertFalse(result.isEmpty());
        assertTrue(result.contains(IDENTIFIER));
    }

    @Test
    void replaceDoesNothingIfThereIsNoComponentToReplaceForTheGivenIdentifier() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        boolean result = testSubject.replace(IDENTIFIER, old -> {
            invoked.set(true);
            return new InstantiatedComponentDefinition<>(IDENTIFIER, "replacement");
        });

        assertFalse(invoked.get());
        assertFalse(result);
    }

    @Test
    void replaceReplacesComponents() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        testSubject.put(new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state"));

        boolean result = testSubject.replace(IDENTIFIER, old -> {
            invoked.set(true);
            return new InstantiatedComponentDefinition<>(IDENTIFIER, "replacement");
        });

        assertTrue(invoked.get());
        assertTrue(result);
        Optional<String> resultComponent = testSubject.get(IDENTIFIER).map(c -> c.resolve(mock()));
        assertTrue(resultComponent.isPresent());
        assertEquals("replacement", resultComponent.get());
    }

    @Test
    void describeToDescribesComponents() {
        ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);
        Component<String> testComponent = new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state");
        testSubject.put(testComponent);

        testSubject.describeTo(testDescriptor);

        verify(testDescriptor).describeProperty("components", Map.of(IDENTIFIER, testComponent));
    }

    @Test
    void listReturnsReadOnlyViewOfComponents() {
        Identifier<Object> identifier2 = new Identifier<>(Object.class, "test2");
        Set<Identifier<?>> identifiersBeforePut = testSubject.identifiers();

        testSubject.put(new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state"));
        testSubject.put(new InstantiatedComponentDefinition<>(identifier2, "some-state"));

        Set<Identifier<?>> identifiersAfterPut = testSubject.identifiers();
        assertFalse(identifiersBeforePut.contains(IDENTIFIER));
        assertTrue(identifiersAfterPut.contains(IDENTIFIER));
        assertTrue(identifiersAfterPut.contains(identifier2));

        assertThrows(UnsupportedOperationException.class, () -> identifiersAfterPut.add(identifier2));
    }

    @Test
    void postProcessComponentsProvidesAllAvailableComponents() {
        Identifier<Object> identifier2 = new Identifier<>(Object.class, "test2");

        testSubject.put(new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state"));
        testSubject.put(new InstantiatedComponentDefinition<>(identifier2, "some-state"));

        List<Component<?>> visited = new ArrayList<>();
        testSubject.postProcessComponents(visited::add);
        assertEquals(2, visited.size());
    }

    @Test
    void postProcessComponentsRethrowsExceptions() {
        Identifier<Object> identifier2 = new Identifier<>(Object.class, "test2");

        testSubject.put(new InstantiatedComponentDefinition<>(IDENTIFIER, "some-state"));
        testSubject.put(new InstantiatedComponentDefinition<>(identifier2, "some-state"));

        assertThrows(MockException.class, () -> testSubject.postProcessComponents(c -> {
            throw new MockException();
        }));
    }
}
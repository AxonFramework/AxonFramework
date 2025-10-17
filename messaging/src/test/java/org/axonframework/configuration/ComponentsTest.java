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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.interceptors.InterceptingCommandBus;
import org.axonframework.commandhandling.retry.RetryingCommandBus;
import org.axonframework.commandhandling.tracing.TracingCommandBus;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Component.Identifier;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void containsMatchesWithExactNameOnly() {
        // given...
        Identifier<InterceptingCommandBus> idOne = new Identifier<>(InterceptingCommandBus.class, InterceptingCommandBus.class.getName());
        InterceptingCommandBus mockOne = mock(InterceptingCommandBus.class);
        testSubject.put(new InstantiatedComponentDefinition<>(idOne, mockOne));

        Identifier<RetryingCommandBus> idTwo = new Identifier<>(RetryingCommandBus.class, RetryingCommandBus.class.getName());
        RetryingCommandBus mockTwo = mock(RetryingCommandBus.class);
        testSubject.put(new InstantiatedComponentDefinition<>(idTwo, mockTwo));

        // when/then...
        // exact type and name match succeeds...
        assertTrue(testSubject.contains(new Identifier<>(InterceptingCommandBus.class, InterceptingCommandBus.class.getName())));
        assertTrue(testSubject.contains(new Identifier<>(RetryingCommandBus.class, RetryingCommandBus.class.getName())));
        // parent type with different FQCN fails (names don't match)...
        assertFalse(testSubject.contains(new Identifier<>(CommandBus.class, CommandBus.class.getName())));
        // non-existent type fails...
        assertFalse(testSubject.contains(new Identifier<>(TracingCommandBus.class, TracingCommandBus.class.getName())));
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
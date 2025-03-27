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
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
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
        Component<String> testComponent = new Component<>(IDENTIFIER, c -> "some-state");

        testSubject.put(testComponent);

        Optional<Component<String>> result = testSubject.get(IDENTIFIER);
        assertTrue(result.isPresent());
        assertEquals(testComponent, result.get());
    }

    @Test
    void computeIfAbsentDoesNotComputeIfIdentifierIsAlreadyPresent() {
        Component<String> testComponent = new Component<>(IDENTIFIER, c -> "some-state");
        AtomicBoolean invoked = new AtomicBoolean(false);

        testSubject.put(testComponent);
        testSubject.computeIfAbsent(IDENTIFIER, id -> {
            invoked.set(true);
            return mock(Component.class);
        });


        assertFalse(invoked.get());
        Optional<Component<String>> optionalResult = testSubject.get(IDENTIFIER);
        assertTrue(optionalResult.isPresent());
        assertEquals(testComponent, optionalResult.get());
    }

    @Test
    void computeIfAbsentComputesForAbsentIdentifier() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Component<String> testComponent = new Component<>(IDENTIFIER, c -> "some-state");

        testSubject.computeIfAbsent(IDENTIFIER, id -> {
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
        Component<String> original = new Component<>(IDENTIFIER, c -> "some-state");
        Component<String> replacement = new Component<>(IDENTIFIER, c -> "other-state");

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
        testSubject.put(new Component<>(IDENTIFIER, c -> "some-state"));

        assertTrue(testSubject.contains(IDENTIFIER));
        assertFalse(testSubject.contains(unknownIdentifier));
    }

    @Test
    void listReturnsReadOnlyViewOfComponents() {
        Identifier<Object> identifier2 = new Identifier<>(Object.class, "test2");
        Set<Identifier<?>> identifiersBeforePut = testSubject.listComponents();

        testSubject.put(new Component<>(IDENTIFIER, c -> "some-state"));
        testSubject.put(new Component<>(identifier2, c -> "some-state"));

        Set<Identifier<?>> identifiersAfterPut = testSubject.listComponents();
        assertFalse(identifiersBeforePut.contains(IDENTIFIER));
        assertTrue(identifiersAfterPut.contains(IDENTIFIER));
        assertTrue(identifiersAfterPut.contains(identifier2));

        assertThrows(UnsupportedOperationException.class, () -> identifiersAfterPut.add(identifier2));
    }

    @Test
    void postProcessComponentsProvidesAllAvailableComponents() {
        Identifier<Object> identifier2 = new Identifier<>(Object.class, "test2");

        testSubject.put(new Component<>(IDENTIFIER, c -> "some-state"));
        testSubject.put(new Component<>(identifier2, c -> "some-state"));

        List<Component<?>> visited = new ArrayList<>();
        testSubject.postProcessComponents(visited::add);
        assertEquals(2, visited.size());
    }

    @Test
    void postProcessComponentsRethrowsExceptions() {
        Identifier<Object> identifier2 = new Identifier<>(Object.class, "test2");

        testSubject.put(new Component<>(IDENTIFIER, c -> "some-state"));
        testSubject.put(new Component<>(identifier2, c -> "some-state"));

        assertThrows(MockException.class, () -> testSubject.postProcessComponents(c -> {
            throw new MockException();
        }));
    }
}
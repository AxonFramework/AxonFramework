/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.modelling.command.inspection.EntityModel;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.axonframework.messaging.QualifiedName.dottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForwardMatchingInstancesTest {

    @SuppressWarnings("unchecked")
    private final EntityModel<Object> entityModel = mock();

    private ForwardMatchingInstances<Message<?>> testSubject;

    @AggregateMember
    private final Object stubEntityWithImplicitRoutingKey = null;

    @AggregateMember(routingKey = "explicitRoutingKey")
    private final Object stubEntityWithExplicitRoutingKey = null;

    private final MockPropertyAccessStrategy mockAccessStrategy = mock();

    @BeforeEach
    void setUp() {
        PropertyAccessStrategy.register(mockAccessStrategy);
        testSubject = new ForwardMatchingInstances<>();
        when(entityModel.routingKey()).thenReturn("implicitRoutingKey");
    }

    @AfterEach
    void tearDown() {
        PropertyAccessStrategy.unregister(mockAccessStrategy);
    }

    @Test
    void useImplicitRoutingKeyWhenNotDefined() throws Exception {
        Field member = getClass().getDeclaredField("stubEntityWithImplicitRoutingKey");

        testSubject.initialize(member, entityModel);

        verify(entityModel).routingKey();

        String candidate1 = "Candidate1";
        Message<String> testMessage = new GenericMessage<>(dottedName("test.message"), "Mock");

        Stream<String> result = testSubject.filterCandidates(testMessage, Stream.of(candidate1));

        verify(mockAccessStrategy).propertyFor(String.class, "implicitRoutingKey");
        assertEquals(0, result.count());
    }

    @Test
    void useExplicitRoutingKeyWhenDefined() throws Exception {
        Field member = getClass().getDeclaredField("stubEntityWithExplicitRoutingKey");

        testSubject.initialize(member, entityModel);

        verify(entityModel).routingKey();

        String candidate1 = "Candidate1";
        Message<String> testMessage = new GenericMessage<>(dottedName("test.message"), "Mock");

        Stream<String> result = testSubject.filterCandidates(testMessage, Stream.of(candidate1));

        verify(mockAccessStrategy).propertyFor(String.class, "explicitRoutingKey");
        assertEquals(0, result.count());
    }

    @Test
    void candidateReturnedWhenPropertyMatchesIdentifier() throws Exception {
        Field member = getClass().getDeclaredField("stubEntityWithExplicitRoutingKey");
        Property<String> mockProperty = mock();
        when(entityModel.getIdentifier(any())).thenReturn("ID");
        when(mockAccessStrategy.propertyFor(String.class, "explicitRoutingKey")).thenReturn(mockProperty);
        when(mockProperty.getValue(any())).thenReturn("ID");

        testSubject.initialize(member, entityModel);

        verify(entityModel).routingKey();

        String candidate1 = "Candidate1";
        String payload = "Mock";
        Message<String> testMessage = new GenericMessage<>(dottedName("test.message"), payload);

        Stream<String> result = testSubject.filterCandidates(testMessage, Stream.of(candidate1));

        verify(mockAccessStrategy).propertyFor(String.class, "explicitRoutingKey");
        verify(mockProperty).getValue(same(payload));
        assertEquals(1, result.count());
    }

    @Test
    void candidateIgnoredWhenPropertyDoesNotMatchIdentifier() throws Exception {
        Field member = getClass().getDeclaredField("stubEntityWithExplicitRoutingKey");
        Property<String> mockProperty = mock();
        when(entityModel.getIdentifier(any())).thenReturn("ID");
        when(mockAccessStrategy.propertyFor(String.class, "explicitRoutingKey")).thenReturn(mockProperty);
        when(mockProperty.getValue(any())).thenReturn("ID2");

        testSubject.initialize(member, entityModel);

        verify(entityModel).routingKey();

        String candidate1 = "Candidate1";
        String payload = "Mock";
        Message<String> testMessage = new GenericMessage<>(dottedName("test.message"), payload);

        Stream<String> result = testSubject.filterCandidates(testMessage, Stream.of(candidate1));

        verify(mockAccessStrategy).propertyFor(String.class, "explicitRoutingKey");
        verify(mockProperty).getValue(same(payload));
        assertEquals(0, result.count());
    }

    @Test
    void secondRequestUsesCachedProperty() throws Exception {
        Field member = getClass().getDeclaredField("stubEntityWithExplicitRoutingKey");
        Property<String> mockProperty = mock();
        when(entityModel.getIdentifier(any())).thenReturn("ID");
        when(mockAccessStrategy.propertyFor(String.class, "explicitRoutingKey")).thenReturn(mockProperty);
        when(mockProperty.getValue(any())).thenReturn("ID");

        testSubject.initialize(member, entityModel);

        verify(entityModel).routingKey();

        String candidate1 = "Candidate1";
        String payload1 = "Mock1";
        String payload2 = "Mock2";
        Message<String> testMessageOne = new GenericMessage<>(dottedName("test.message"), payload1);
        Message<String> testMessageTwo = new GenericMessage<>(dottedName("test.message"), payload2);

        Stream<String> result1 = testSubject.filterCandidates(testMessageOne, Stream.of(candidate1));
        Stream<String> result2 = testSubject.filterCandidates(testMessageTwo, Stream.of(candidate1));

        // the access strategy is only consulted the first time or any payload
        verify(mockAccessStrategy, times(1)).propertyFor(String.class, "explicitRoutingKey");
        verify(mockProperty, times(1)).getValue(same(payload1));
        verify(mockProperty, times(1)).getValue(same(payload2));
        assertEquals(1, result1.count());
        assertEquals(1, result2.count());
    }

    @Test
    void secondRequestForDifferentPayloadUsesDifferentProperty() throws Exception {
        Field member = getClass().getDeclaredField("stubEntityWithExplicitRoutingKey");
        Property<String> mockStringProperty = mock();
        Property<Long> mockLongProperty = mock();
        when(entityModel.getIdentifier(any())).thenReturn("ID");
        when(mockAccessStrategy.propertyFor(String.class, "explicitRoutingKey")).thenReturn(mockStringProperty);
        when(mockStringProperty.getValue(any())).thenReturn("ID");
        when(mockAccessStrategy.propertyFor(Long.class, "explicitRoutingKey")).thenReturn(mockLongProperty);
        when(mockLongProperty.getValue(any())).thenReturn("ID");

        testSubject.initialize(member, entityModel);

        verify(entityModel).routingKey();

        String candidate1 = "Candidate1";
        String payload1 = "Mock1";
        Long payload2 = 2L;
        Message<String> testMessageOne = new GenericMessage<>(dottedName("test.message"), payload1);
        Message<Long> testMessageTwo = new GenericMessage<>(dottedName("test.message"), payload2);

        Stream<String> result1 = testSubject.filterCandidates(testMessageOne, Stream.of(candidate1));
        Stream<String> result2 = testSubject.filterCandidates(testMessageTwo, Stream.of(candidate1));

        // the access strategy is only consulted the first time or any payload
        verify(mockAccessStrategy, times(1)).propertyFor(String.class, "explicitRoutingKey");
        verify(mockAccessStrategy, times(1)).propertyFor(Long.class, "explicitRoutingKey");
        verify(mockStringProperty, times(1)).getValue(same(payload1));
        verify(mockLongProperty, times(1)).getValue(same(payload2));
        assertEquals(1, result1.count());
        assertEquals(1, result2.count());
    }


    private abstract static class MockPropertyAccessStrategy extends PropertyAccessStrategy {


        @Override
        public int getPriority() {
            return Integer.MAX_VALUE;
        }

        @Override
        public abstract <T> Property<T> propertyFor(Class<? extends T> targetClass, String property);
    }
}
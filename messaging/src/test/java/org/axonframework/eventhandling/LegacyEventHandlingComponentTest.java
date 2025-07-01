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

package org.axonframework.eventhandling;

import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link LegacyEventHandlingComponent}.
 */
class LegacyEventHandlingComponentTest {

    @Mock
    private EventHandlerInvoker mockInvoker;

    @Mock
    private EventMessage<?> mockEvent;

    @Mock
    private ProcessingContext mockContext;

    private LegacyEventHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testSubject = new LegacyEventHandlingComponent(mockInvoker);
    }

    @Test
    void supportedEvents_shouldConvertEventTypesToQualifiedNames() {
        // Given
        Set<Class<?>> supportedTypes = Set.of(String.class, Integer.class);
        when(mockInvoker.supportedEventTypes()).thenReturn(supportedTypes);

        // When
        Set<QualifiedName> result = testSubject.supportedEvents();

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains(new QualifiedName(String.class)));
        assertTrue(result.contains(new QualifiedName(Integer.class)));
    }

    @Test
    void supportedEvents_shouldReturnEmptySetWhenInvokerReturnsEmpty() {
        // Given
        when(mockInvoker.supportedEventTypes()).thenReturn(Set.of());

        // When
        Set<QualifiedName> result = testSubject.supportedEvents();

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void isSupported_shouldReturnTrueWhenEventIsSupported() {
        // Given
        Set<Class<?>> supportedTypes = Set.of(String.class);
        when(mockInvoker.supportedEventTypes()).thenReturn(supportedTypes);
        QualifiedName eventName = new QualifiedName(String.class);

        // When
        boolean result = testSubject.isSupported(eventName);

        // Then
        assertTrue(result);
    }

    @Test
    void isSupported_shouldReturnFalseWhenEventIsNotSupported() {
        // Given
        Set<Class<?>> supportedTypes = Set.of(String.class);
        when(mockInvoker.supportedEventTypes()).thenReturn(supportedTypes);
        QualifiedName eventName = new QualifiedName(Integer.class);

        // When
        boolean result = testSubject.isSupported(eventName);

        // Then
        assertFalse(result);
    }

    @Test
    void isSupported_shouldReturnFalseWhenNoSupportedEventsProvided() {
        when(mockInvoker.supportedEventTypes()).thenReturn(Set.of());
        QualifiedName eventName = new QualifiedName(String.class);

        // When
        boolean result = testSubject.isSupported(eventName);

        // Then
        assertFalse(result);
    }

    @Test
    void handle_shouldDelegateToInvoker() throws Exception {
        // Given
        when(mockInvoker.supportedEventTypes()).thenReturn(Set.of());

        // When
        testSubject.handle(mockEvent, mockContext);

        // Then
        verify(mockInvoker).handle(eq(mockEvent), eq(mockContext), any(Segment.class));
    }

    @Test
    void getEventHandlerInvoker_shouldReturnWrappedInvoker() {
        // When
        EventHandlerInvoker result = testSubject.getEventHandlerInvoker();

        // Then
        assertSame(mockInvoker, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sequenceIdentifierFor_shouldReturnSequenceIdentifierFromSimpleEventHandlerInvoker() {
        // Given
        SimpleEventHandlerInvoker simpleInvoker = mock(SimpleEventHandlerInvoker.class);
        SequencingPolicy<EventMessage<?>> sequencingPolicy = mock(SequencingPolicy.class);
        EventMessage<?> event = mock(EventMessage.class);
        Object expectedSequenceId = "test-sequence-id";

        when(simpleInvoker.getSequencingPolicy()).thenReturn((SequencingPolicy) sequencingPolicy);
        when(sequencingPolicy.getSequenceIdentifierFor(event)).thenReturn(Optional.of(expectedSequenceId));

        LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(simpleInvoker);

        // When
        Optional<Object> result = component.sequenceIdentifierFor(event);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expectedSequenceId, result.get());
        verify(sequencingPolicy).getSequenceIdentifierFor(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sequenceIdentifierFor_shouldReturnEmptyFromSimpleEventHandlerInvokerWhenPolicyReturnsEmpty() {
        // Given
        SimpleEventHandlerInvoker simpleInvoker = mock(SimpleEventHandlerInvoker.class);
        SequencingPolicy<EventMessage<?>> sequencingPolicy = mock(SequencingPolicy.class);
        EventMessage<?> event = mock(EventMessage.class);

        when(simpleInvoker.getSequencingPolicy()).thenReturn((SequencingPolicy) sequencingPolicy);
        when(sequencingPolicy.getSequenceIdentifierFor(event)).thenReturn(Optional.empty());

        LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(simpleInvoker);

        // When
        Optional<Object> result = component.sequenceIdentifierFor(event);

        // Then
        assertFalse(result.isPresent());
        verify(sequencingPolicy).getSequenceIdentifierFor(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sequenceIdentifierFor_shouldReturnSequenceIdentifierFromMultiEventHandlerInvokerWithSimpleDelegate() {
        // Given
        MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
        SimpleEventHandlerInvoker simpleInvoker = mock(SimpleEventHandlerInvoker.class);
        SequencingPolicy<EventMessage<?>> sequencingPolicy = mock(SequencingPolicy.class);
        EventMessage<?> event = mock(EventMessage.class);
        Object expectedSequenceId = "multi-sequence-id";

        when(multiInvoker.delegates()).thenReturn(List.of(simpleInvoker));
        when(simpleInvoker.getSequencingPolicy()).thenReturn((SequencingPolicy) sequencingPolicy);
        when(sequencingPolicy.getSequenceIdentifierFor(event)).thenReturn(Optional.of(expectedSequenceId));

        LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

        // When
        Optional<Object> result = component.sequenceIdentifierFor(event);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expectedSequenceId, result.get());
        verify(sequencingPolicy).getSequenceIdentifierFor(event);
    }

    @Test
    void sequenceIdentifierFor_shouldReturnEmptyFromMultiEventHandlerInvokerWithNonSimpleDelegate() {
        // Given
        MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
        EventHandlerInvoker otherInvoker = mock(EventHandlerInvoker.class);
        EventMessage<?> event = mock(EventMessage.class);

        when(multiInvoker.delegates()).thenReturn(List.of(otherInvoker));

        LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

        // When
        Optional<Object> result = component.sequenceIdentifierFor(event);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void sequenceIdentifierFor_shouldReturnEmptyFromMultiEventHandlerInvokerWithEmptyDelegates() {
        // Given
        MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
        EventMessage<?> event = mock(EventMessage.class);

        when(multiInvoker.delegates()).thenReturn(List.of());

        LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

        // When
        Optional<Object> result = component.sequenceIdentifierFor(event);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void sequenceIdentifierFor_shouldReturnEmptyFromUnsupportedEventHandlerInvokerType() {
        // Given
        EventHandlerInvoker unsupportedInvoker = mock(EventHandlerInvoker.class);
        EventMessage<?> event = mock(EventMessage.class);

        LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(unsupportedInvoker);

        // When
        Optional<Object> result = component.sequenceIdentifierFor(event);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void sequenceIdentifierFor_shouldHandleMultipleNonSimpleDelegatesInMultiInvoker() {
        // Given
        MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
        EventHandlerInvoker firstInvoker = mock(EventHandlerInvoker.class);
        EventHandlerInvoker secondInvoker = mock(EventHandlerInvoker.class);
        EventMessage<?> event = mock(EventMessage.class);

        when(multiInvoker.delegates()).thenReturn(List.of(firstInvoker, secondInvoker));

        LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

        // When
        Optional<Object> result = component.sequenceIdentifierFor(event);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void sequenceIdentifierFor_shouldOnlyCheckFirstDelegateInMultiInvoker() {
        // Given
        MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
        EventHandlerInvoker firstInvoker = mock(EventHandlerInvoker.class);
        SimpleEventHandlerInvoker secondInvoker = mock(SimpleEventHandlerInvoker.class);
        EventMessage<?> event = mock(EventMessage.class);

        when(multiInvoker.delegates()).thenReturn(List.of(firstInvoker, secondInvoker));

        LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

        // When
        Optional<Object> result = component.sequenceIdentifierFor(event);

        // Then
        assertFalse(result.isPresent());
        // Verify that secondInvoker (which is SimpleEventHandlerInvoker) is never called
        verifyNoInteractions(secondInvoker);
    }

}

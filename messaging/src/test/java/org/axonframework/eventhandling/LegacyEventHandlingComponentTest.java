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

import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

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
} 
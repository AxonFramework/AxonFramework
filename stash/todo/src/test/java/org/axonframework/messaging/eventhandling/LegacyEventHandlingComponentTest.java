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

package org.axonframework.messaging.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.sequencing.SequencingPolicy;
import org.axonframework.messaging.core.unitofwork.LegacyMessageSupportingContext;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link LegacyEventHandlingComponent}.
 */
class LegacyEventHandlingComponentTest {

    @Mock
    private org.axonframework.messaging.eventhandling.EventHandlerInvoker mockInvoker;

    @Mock
    private EventMessage mockEvent;

    @Mock
    private ProcessingContext mockContext;

    private LegacyEventHandlingComponent testSubject;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testSubject = new LegacyEventHandlingComponent(mockInvoker);
    }

    @Nested
    class SupportedEvents {

        @Test
        void shouldConvertEventTypesToQualifiedNames() {
            //given
            Set<Class<?>> supportedTypes = Set.of(String.class, Integer.class);
            when(mockInvoker.supportedEventTypes()).thenReturn(supportedTypes);

            //when
            Set<QualifiedName> result = testSubject.supportedEvents();

            //then
            assertThat(result).hasSize(2);
            assertThat(result).contains(new QualifiedName(String.class));
            assertThat(result).contains(new QualifiedName(Integer.class));
        }

        @Test
        void shouldReturnEmptySetWhenInvokerReturnsEmpty() {
            //given
            when(mockInvoker.supportedEventTypes()).thenReturn(Set.of());

            //when
            Set<QualifiedName> result = testSubject.supportedEvents();

            //then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class IsSupported {

        @Test
        void shouldReturnTrueWhenEventIsSupported() {
            //given
            Set<Class<?>> supportedTypes = Set.of(String.class);
            when(mockInvoker.supportedEventTypes()).thenReturn(supportedTypes);
            QualifiedName eventName = new QualifiedName(String.class);

            //when
            boolean result = testSubject.supports(eventName);

            //then
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenEventIsNotSupported() {
            //given
            Set<Class<?>> supportedTypes = Set.of(String.class);
            when(mockInvoker.supportedEventTypes()).thenReturn(supportedTypes);
            QualifiedName eventName = new QualifiedName(Integer.class);

            //when
            boolean result = testSubject.supports(eventName);

            //then
            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnFalseWhenNoSupportedEventsProvided() {
            //given
            when(mockInvoker.supportedEventTypes()).thenReturn(Set.of());
            QualifiedName eventName = new QualifiedName(String.class);

            //when
            boolean result = testSubject.supports(eventName);

            //then
            assertThat(result).isFalse();
        }
    }

    @Test
    void handle_shouldDelegateToInvoker() throws Exception {
        //given
        when(mockInvoker.supportedEventTypes()).thenReturn(Set.of());

        //when
        testSubject.handle(mockEvent, mockContext);

        //then
        verify(mockInvoker).handle(eq(mockEvent), eq(mockContext), any(Segment.class));
    }

    @Test
    void getEventHandlerInvoker_shouldReturnWrappedInvoker() {
        //when
        org.axonframework.messaging.eventhandling.EventHandlerInvoker result = testSubject.getEventHandlerInvoker();

        //then
        assertThat(result).isSameAs(mockInvoker);
    }

    @Nested
    class SequenceIdentifierFor {

        @Test
        void shouldReturnSequenceIdentifierFromSimpleEventHandlerInvoker() {
            //given
            SimpleEventHandlerInvoker simpleInvoker = mock(SimpleEventHandlerInvoker.class);
            SequencingPolicy<EventMessage> sequencingPolicy = mock(SequencingPolicy.class);
            EventMessage event = mock(EventMessage.class);
            Object expectedSequenceId = "test-sequence-id";
            ProcessingContext processingContext = processingContextWith(event);

            when(simpleInvoker.getSequencingPolicy()).thenAnswer(i -> sequencingPolicy);
            when(sequencingPolicy.sequenceIdentifierFor(event, processingContext)).thenReturn(Optional.of(
                    expectedSequenceId));

            LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(simpleInvoker);

            //when
            var result = component.sequenceIdentifierFor(event, processingContext);

            //then
            assertThat(result).isEqualTo(expectedSequenceId);
            verify(sequencingPolicy).sequenceIdentifierFor(event, processingContext);
        }

        @Test
        void shouldReturnEventIdentifierFromSimpleEventHandlerInvokerWhenPolicyReturnsEmpty() {
            //given
            SimpleEventHandlerInvoker simpleInvoker = mock(SimpleEventHandlerInvoker.class);
            SequencingPolicy<EventMessage> sequencingPolicy = mock(SequencingPolicy.class);
            EventMessage event = mock(EventMessage.class);
            ProcessingContext processingContext = processingContextWith(event);

            when(simpleInvoker.getSequencingPolicy()).thenAnswer(i -> sequencingPolicy);
            when(sequencingPolicy.sequenceIdentifierFor(event, processingContext)).thenReturn(Optional.empty());

            LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(simpleInvoker);

            //when
            var result = component.sequenceIdentifierFor(event, processingContext);

            //then
            assertThat(result).isEqualTo(event.identifier());
            verify(sequencingPolicy).sequenceIdentifierFor(event, processingContext);
        }

        @Test
        void shouldReturnSequenceIdentifierFromMultiEventHandlerInvokerWithSimpleDelegate() {
            //given
            MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
            SimpleEventHandlerInvoker simpleInvoker = mock(SimpleEventHandlerInvoker.class);
            SequencingPolicy<EventMessage> sequencingPolicy = mock(SequencingPolicy.class);
            EventMessage event = mock(EventMessage.class);
            Object expectedSequenceId = "multi-sequence-id";
            ProcessingContext processingContext = processingContextWith(event);

            when(multiInvoker.delegates()).thenReturn(List.of(simpleInvoker));
            when(simpleInvoker.getSequencingPolicy()).thenAnswer(i -> sequencingPolicy);
            when(sequencingPolicy.sequenceIdentifierFor(event, processingContext)).thenReturn(Optional.of(
                    expectedSequenceId));

            LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

            //when
            var result = component.sequenceIdentifierFor(event, processingContext);

            //then
            assertThat(result).isEqualTo(expectedSequenceId);
            verify(sequencingPolicy).sequenceIdentifierFor(event, processingContext);
        }

        @Test
        void shouldReturnEventIdentifierFromMultiEventHandlerInvokerWithNonSimpleDelegate() {
            //given
            MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
            org.axonframework.messaging.eventhandling.EventHandlerInvoker otherInvoker = mock(org.axonframework.messaging.eventhandling.EventHandlerInvoker.class);
            EventMessage event = mock(EventMessage.class);

            when(multiInvoker.delegates()).thenReturn(List.of(otherInvoker));

            LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

            //when
            var result = component.sequenceIdentifierFor(event, processingContextWith(event));

            //then
            assertThat(result).isEqualTo(event.identifier());
        }

        @Test
        void shouldReturnEventIdentifierFromMultiEventHandlerInvokerWithEmptyDelegates() {
            //given
            MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
            EventMessage event = mock(EventMessage.class);

            when(multiInvoker.delegates()).thenReturn(List.of());

            LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

            //when
            var result = component.sequenceIdentifierFor(event, processingContextWith(event));

            //then
            assertThat(result).isEqualTo(event.identifier());
        }

        @Test
        void shouldReturnEventIdentifierFromUnsupportedEventHandlerInvokerType() {
            //given
            org.axonframework.messaging.eventhandling.EventHandlerInvoker unsupportedInvoker = mock(org.axonframework.messaging.eventhandling.EventHandlerInvoker.class);
            EventMessage event = mock(EventMessage.class);

            LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(unsupportedInvoker);

            //when
            var result = component.sequenceIdentifierFor(event, processingContextWith(event));

            //then
            assertThat(result).isEqualTo(event.identifier());
        }

        @Test
        void shouldHandleMultipleNonSimpleDelegatesInMultiInvoker() {
            //given
            MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
            org.axonframework.messaging.eventhandling.EventHandlerInvoker firstInvoker = mock(org.axonframework.messaging.eventhandling.EventHandlerInvoker.class);
            org.axonframework.messaging.eventhandling.EventHandlerInvoker secondInvoker = mock(org.axonframework.messaging.eventhandling.EventHandlerInvoker.class);
            EventMessage event = mock(EventMessage.class);

            when(multiInvoker.delegates()).thenReturn(List.of(firstInvoker, secondInvoker));

            LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

            //when
            var result = component.sequenceIdentifierFor(event, processingContextWith(event));

            //then
            assertThat(result).isEqualTo(event.identifier());
        }

        @Test
        void shouldOnlyCheckFirstDelegateInMultiInvoker() {
            //given
            MultiEventHandlerInvoker multiInvoker = mock(MultiEventHandlerInvoker.class);
            org.axonframework.messaging.eventhandling.EventHandlerInvoker firstInvoker = mock(EventHandlerInvoker.class);
            SimpleEventHandlerInvoker secondInvoker = mock(SimpleEventHandlerInvoker.class);
            EventMessage event = mock(EventMessage.class);

            when(multiInvoker.delegates()).thenReturn(List.of(firstInvoker, secondInvoker));

            LegacyEventHandlingComponent component = new LegacyEventHandlingComponent(multiInvoker);

            //when
            var result = component.sequenceIdentifierFor(event, processingContextWith(event));

            //then
            assertThat(result).isEqualTo(event.identifier());
            // Verify that secondInvoker (which is SimpleEventHandlerInvoker) is never called
            verifyNoInteractions(secondInvoker);
        }
    }

    @Nonnull
    private static LegacyMessageSupportingContext processingContextWith(EventMessage event) {
        return new LegacyMessageSupportingContext(event);
    }
}

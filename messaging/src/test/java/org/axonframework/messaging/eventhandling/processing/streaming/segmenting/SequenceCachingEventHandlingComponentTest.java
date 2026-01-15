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

package org.axonframework.messaging.eventhandling.processing.streaming.segmenting;

import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventHandler;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class validating the {@link SequenceCachingEventHandlingComponent} functionality.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SequenceCachingEventHandlingComponentTest {

    private EventHandlingComponent delegate;
    private SequenceCachingEventHandlingComponent cachingComponent;

    @BeforeEach
    void setUp() {
        delegate = Mockito.mock(EventHandlingComponent.class);
        cachingComponent = new SequenceCachingEventHandlingComponent(delegate);
    }

    @Nested
    class CachingBehaviorTest {

        @Test
        void shouldCacheSequenceIdentifierForSameEvent() {
            // given
            EventMessage event = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();
            Object expectedSequenceId = "sequence-123";

            when(delegate.sequenceIdentifierFor(event, context)).thenReturn(expectedSequenceId);

            // when
            Object firstCall = cachingComponent.sequenceIdentifierFor(event, context);
            Object secondCall = cachingComponent.sequenceIdentifierFor(event, context);

            // then
            assertThat(firstCall).isEqualTo(expectedSequenceId);
            assertThat(secondCall).isEqualTo(expectedSequenceId);
            verify(delegate, times(1)).sequenceIdentifierFor(event, context);
        }

        @Test
        void shouldInvokeDelegateForDifferentEvents() {
            // given
            EventMessage event1 = EventTestUtils.asEventMessage("payload-1");
            EventMessage event2 = EventTestUtils.asEventMessage("payload-2");
            ProcessingContext context = new StubProcessingContext();
            Object sequenceId1 = "sequence-1";
            Object sequenceId2 = "sequence-2";

            when(delegate.sequenceIdentifierFor(event1, context)).thenReturn(sequenceId1);
            when(delegate.sequenceIdentifierFor(event2, context)).thenReturn(sequenceId2);

            // when
            Object result1 = cachingComponent.sequenceIdentifierFor(event1, context);
            Object result2 = cachingComponent.sequenceIdentifierFor(event2, context);

            // then
            assertThat(result1).isEqualTo(sequenceId1);
            assertThat(result2).isEqualTo(sequenceId2);
            verify(delegate, times(1)).sequenceIdentifierFor(event1, context);
            verify(delegate, times(1)).sequenceIdentifierFor(event2, context);
        }

        @Test
        void shouldCacheDifferentSequenceIdentifiersForDifferentEvents() {
            // given
            EventMessage event1 = EventTestUtils.asEventMessage("payload-1");
            EventMessage event2 = EventTestUtils.asEventMessage("payload-2");
            ProcessingContext context = new StubProcessingContext();
            Object sequenceId1 = "sequence-1";
            Object sequenceId2 = "sequence-2";

            when(delegate.sequenceIdentifierFor(event1, context)).thenReturn(sequenceId1);
            when(delegate.sequenceIdentifierFor(event2, context)).thenReturn(sequenceId2);

            // when
            cachingComponent.sequenceIdentifierFor(event1, context);
            cachingComponent.sequenceIdentifierFor(event2, context);
            Object cachedResult1 = cachingComponent.sequenceIdentifierFor(event1, context);
            Object cachedResult2 = cachingComponent.sequenceIdentifierFor(event2, context);

            // then
            assertThat(cachedResult1).isEqualTo(sequenceId1);
            assertThat(cachedResult2).isEqualTo(sequenceId2);
            verify(delegate, times(1)).sequenceIdentifierFor(event1, context);
            verify(delegate, times(1)).sequenceIdentifierFor(event2, context);
        }
    }

    @Nested
    class ProcessingContextScopeTest {

        @Test
        void shouldNotCacheAcrossDifferentProcessingContexts() {
            // given
            EventMessage event = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context1 = new StubProcessingContext();
            ProcessingContext context2 = new StubProcessingContext();
            Object sequenceId1 = "sequence-1";
            Object sequenceId2 = "sequence-2";

            when(delegate.sequenceIdentifierFor(event, context1)).thenReturn(sequenceId1);
            when(delegate.sequenceIdentifierFor(event, context2)).thenReturn(sequenceId2);

            // when
            Object result1 = cachingComponent.sequenceIdentifierFor(event, context1);
            Object result2 = cachingComponent.sequenceIdentifierFor(event, context2);

            // then
            assertThat(result1).isEqualTo(sequenceId1);
            assertThat(result2).isEqualTo(sequenceId2);
            verify(delegate, times(1)).sequenceIdentifierFor(event, context1);
            verify(delegate, times(1)).sequenceIdentifierFor(event, context2);
        }

        @Test
        void shouldCachePerProcessingContext() {
            // given
            EventMessage event = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context1 = new StubProcessingContext();
            ProcessingContext context2 = new StubProcessingContext();
            Object sequenceId1 = "sequence-1";
            Object sequenceId2 = "sequence-2";

            when(delegate.sequenceIdentifierFor(event, context1)).thenReturn(sequenceId1);
            when(delegate.sequenceIdentifierFor(event, context2)).thenReturn(sequenceId2);

            // when - call twice per context
            cachingComponent.sequenceIdentifierFor(event, context1);
            cachingComponent.sequenceIdentifierFor(event, context1);
            cachingComponent.sequenceIdentifierFor(event, context2);
            cachingComponent.sequenceIdentifierFor(event, context2);

            // then
            verify(delegate, times(1)).sequenceIdentifierFor(event, context1);
            verify(delegate, times(1)).sequenceIdentifierFor(event, context2);
        }
    }

    @Nested
    class ComplexScenariosTest {

        @Test
        void shouldHandleMixedEventsAndCachingCorrectly() {
            // given
            EventMessage event1 = EventTestUtils.asEventMessage("payload-1");
            EventMessage event2 = EventTestUtils.asEventMessage("payload-2");
            EventMessage event3 = EventTestUtils.asEventMessage("payload-3");
            ProcessingContext context = new StubProcessingContext();

            Object sequenceId1 = "sequence-1";
            Object sequenceId2 = "sequence-2";
            Object sequenceId3 = "sequence-3";

            when(delegate.sequenceIdentifierFor(event1, context)).thenReturn(sequenceId1);
            when(delegate.sequenceIdentifierFor(event2, context)).thenReturn(sequenceId2);
            when(delegate.sequenceIdentifierFor(event3, context)).thenReturn(sequenceId3);

            // when - call in mixed order with repetitions
            cachingComponent.sequenceIdentifierFor(event1, context);  // first call - should invoke delegate
            cachingComponent.sequenceIdentifierFor(event2, context);  // first call - should invoke delegate
            cachingComponent.sequenceIdentifierFor(event1, context);  // second call - should use cache
            cachingComponent.sequenceIdentifierFor(event3, context);  // first call - should invoke delegate
            cachingComponent.sequenceIdentifierFor(event2, context);  // second call - should use cache
            cachingComponent.sequenceIdentifierFor(event3, context);  // second call - should use cache

            // then
            verify(delegate, times(1)).sequenceIdentifierFor(event1, context);
            verify(delegate, times(1)).sequenceIdentifierFor(event2, context);
            verify(delegate, times(1)).sequenceIdentifierFor(event3, context);
        }
    }

    @Nested
    class DelegationTest {

        @Test
        void shouldDelegateHandleToDelegate() {
            // given
            EventMessage event = EventTestUtils.asEventMessage("test-payload");
            ProcessingContext context = new StubProcessingContext();

            // when
            cachingComponent.handle(event, context);

            // then
            verify(delegate).handle(event, context);
        }

        @Test
        void shouldDelegateSubscribeToDelegate() {
            // given
            QualifiedName eventName = new QualifiedName(String.class);
            EventHandler handler = (e, c) -> MessageStream.empty();

            // when
            cachingComponent.subscribe(eventName, handler);

            // then
            verify(delegate).subscribe(eventName, handler);
        }

        @Test
        void shouldDelegateSupportedEventsToDelegate() {
            // when
            cachingComponent.supportedEvents();

            // then
            verify(delegate).supportedEvents();
        }

        @Test
        void shouldDelegateSupportsToDelegate() {
            // given
            QualifiedName eventName = new QualifiedName(String.class);

            // when
            cachingComponent.supports(eventName);

            // then
            verify(delegate).supports(eventName);
        }
    }
}
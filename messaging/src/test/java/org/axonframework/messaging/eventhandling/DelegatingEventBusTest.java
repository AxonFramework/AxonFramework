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

package org.axonframework.messaging.eventhandling;

import org.axonframework.common.FutureUtils;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.DelegatingEventBus;
import org.axonframework.messaging.eventhandling.EventBus;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DelegatingEventBus}.
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 */
class DelegatingEventBusTest {

    private static final MessageType TEST_EVENT_TYPE = new MessageType("event");

    private EventBus delegate;
    private ProcessingContext processingContext;
    private ComponentDescriptor componentDescriptor;

    private TestDelegatingEventBus testSubject;

    @BeforeEach
    void setUp() {
        delegate = mock(EventBus.class);
        processingContext = mock(ProcessingContext.class);
        componentDescriptor = mock(ComponentDescriptor.class);

        testSubject = new TestDelegatingEventBus(delegate);
    }

    @Test
    void publishDelegatesToWrappedEventBus() {
        // given
        EventMessage testEvent = new GenericEventMessage(TEST_EVENT_TYPE, "test");
        List<EventMessage> events = List.of(testEvent);
        CompletableFuture<Void> expectedResult = FutureUtils.emptyCompletedFuture();

        when(delegate.publish(processingContext, events)).thenReturn(expectedResult);

        // when
        CompletableFuture<Void> result = testSubject.publish(processingContext, events);

        // then
        assertSame(expectedResult, result);
        verify(delegate).publish(processingContext, events);
    }

    @Test
    void publishWithNullProcessingContext() {
        // given
        EventMessage testEvent = new GenericEventMessage(TEST_EVENT_TYPE, "test");
        List<EventMessage> events = List.of(testEvent);
        CompletableFuture<Void> expectedResult = FutureUtils.emptyCompletedFuture();

        when(delegate.publish(null, events)).thenReturn(expectedResult);

        // when
        CompletableFuture<Void> result = testSubject.publish(null, events);

        // then
        assertSame(expectedResult, result);
        verify(delegate).publish(null, events);
    }

    @Test
    void subscribeDelegatesToWrappedEventBus() {
        // given
        BiFunction<List<? extends EventMessage>, ProcessingContext, CompletableFuture<?>> eventsBatchConsumer =
            (events, context) -> CompletableFuture.completedFuture(null);
        Registration expectedRegistration = mock(Registration.class);

        when(delegate.subscribe(eventsBatchConsumer)).thenReturn(expectedRegistration);

        // when
        Registration result = testSubject.subscribe(eventsBatchConsumer);

        // then
        assertSame(expectedRegistration, result);
        verify(delegate).subscribe(eventsBatchConsumer);
    }

    @Test
    void describeToDelegatesToWrappedEventBus() {
        // given / when
        testSubject.describeTo(componentDescriptor);

        // then
        verify(delegate).describeTo(componentDescriptor);
        verifyNoMoreInteractions(componentDescriptor);
    }

    /**
     * Concrete test implementation of the abstract {@link DelegatingEventBus} for testing purposes.
     */
    private static class TestDelegatingEventBus extends DelegatingEventBus {

        protected TestDelegatingEventBus(EventBus delegate) {
            super(delegate);
        }
    }
}

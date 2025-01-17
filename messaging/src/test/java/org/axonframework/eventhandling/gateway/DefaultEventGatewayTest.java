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

package org.axonframework.eventhandling.gateway;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * This class validating the {@link DefaultEventGateway}.
 *
 * @author Bert Laverman
 */
class DefaultEventGatewayTest {

    private DefaultEventGateway testSubject;
    private EventBus mockEventBus;
    private MessageDispatchInterceptor<EventMessage<?>> mockEventMessageTransformer;

    @BeforeEach
    void setUp() {
        mockEventBus = mock(EventBus.class);
        //noinspection unchecked
        mockEventMessageTransformer = mock(MessageDispatchInterceptor.class);

        when(mockEventMessageTransformer.handle(isA(EventMessage.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        //noinspection unchecked
        testSubject = DefaultEventGateway.builder()
                                         .eventBus(mockEventBus)
                                         .dispatchInterceptors(mockEventMessageTransformer)
                                         .build();
    }

    @Test
    void publishSingleEvent() {
        // Given
        //noinspection unchecked
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        // When
        testSubject.publish("Event1");

        // Then
        verify(mockEventBus).publish(eventCaptor.capture());
        EventMessage<?> result = eventCaptor.getValue();
        assertEquals("Event1", result.getPayload());

        verify(mockEventMessageTransformer).handle(eventCaptor.capture());
        EventMessage<?> interceptedResult = eventCaptor.getValue();
        assertEquals("Event1", interceptedResult.getPayload());
    }

    @Test
    void publishMultipleEvents() {
        //Given
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        //noinspection unchecked
        ArgumentCaptor<EventMessage<?>> interceptedCaptor = ArgumentCaptor.forClass(EventMessage.class);

        //When
        testSubject.publish("Event2", "Event3");

        //Then
        verify(mockEventBus).publish(eventsCaptor.capture());
        List<EventMessage<?>> result = eventsCaptor.getValue();
        assertEquals(2, result.size());
        assertEquals("Event2", result.get(0).getPayload());
        assertEquals("Event3", result.get(1).getPayload());

        verify(mockEventMessageTransformer, times(2)).handle(interceptedCaptor.capture());
        List<EventMessage<?>> interceptedResult = interceptedCaptor.getAllValues();
        assertEquals(2, interceptedResult.size());
        assertEquals("Event2", interceptedResult.get(0).getPayload());
        assertEquals("Event3", interceptedResult.get(1).getPayload());
    }
}
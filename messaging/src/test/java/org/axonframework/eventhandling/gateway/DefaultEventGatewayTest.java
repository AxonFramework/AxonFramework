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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.argThat;

/**
 * This class validating the {@link DefaultEventGateway}.
 *
 * @author Bert Laverman
 */
class DefaultEventGatewayTest {

    private DefaultEventGateway testSubject;
    private EventSink mockEventSink;

    @BeforeEach
    void setUp() {
        mockEventSink = mock(EventSink.class);
        testSubject = new DefaultEventGateway(
                mockEventSink,
                new ClassBasedMessageTypeResolver()
        );
    }

    @Test
    void publishWithoutContext() {
        // Given
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> eventCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ProcessingContext> contextCaptor = ArgumentCaptor.forClass(ProcessingContext.class);

        // When
        testSubject.publish(null, "Event1");

        // Then
        verify(mockEventSink).publish(contextCaptor.capture(), eventCaptor.capture());
        List<EventMessage<?>> result = eventCaptor.getValue();
        assertEquals("Event1", result.getFirst().getPayload());
        assertEquals("java.lang.String", result.getFirst().type().qualifiedName().name());
        assertNull(contextCaptor.getValue());
    }

    @Test
    void publishWithContext() {
        // Given
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> eventCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ProcessingContext> contextCaptor = ArgumentCaptor.forClass(ProcessingContext.class);
        ProcessingContext testContext = mock(ProcessingContext.class);

        // When
        testSubject.publish(testContext, "Event1");

        // Then
        verify(mockEventSink).publish(contextCaptor.capture(), eventCaptor.capture());
        List<EventMessage<?>> result = eventCaptor.getValue();
        assertEquals("Event1", result.getFirst().getPayload());
        assertEquals("java.lang.String", result.getFirst().type().qualifiedName().name());
        assertEquals(testContext, contextCaptor.getValue());
    }

    @Test
    void publishMultipleEvents() {
        // given
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> eventsCaptor = ArgumentCaptor.forClass(List.class);

        // when
        testSubject.publish(null, "Event2", "Event3");

        // then
        verify(mockEventSink).publish(isNull(), eventsCaptor.capture());
        List<EventMessage<?>> result = eventsCaptor.getValue();
        assertEquals(2, result.size());
        assertEquals("Event2", result.get(0).getPayload());
        assertEquals("java.lang.String", result.getFirst().type().qualifiedName().name());
        assertEquals("Event3", result.get(1).getPayload());
        assertEquals("java.lang.String", result.get(1).type().qualifiedName().name());
    }

    @Test
    void publishEventMessage() {
        // given
        var payload = new TestPayload(UUID.randomUUID().toString());
        var eventMessage = new GenericEventMessage<>(new MessageType("TestPayload"), payload)
                .withMetaData(MetaData.with("key", "value"));

        // when
        testSubject.publish(null, eventMessage);

        // then
        verify(mockEventSink).publish(
                isNull(),
                argThat((List<EventMessage<?>> events) -> events.size() == 1 && events.getFirst().equals(eventMessage))
        );
    }

    private record TestPayload(String value) {

    }
}
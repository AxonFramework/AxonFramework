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
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.*;

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
    void publishSingleEventWithoutContextPublishesWithContext() {
        // Given
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> eventCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ProcessingContext> contextCaptor = ArgumentCaptor.forClass(ProcessingContext.class);

        // When
        testSubject.publish("Event1");

        // Then
        verify(mockEventSink).publish(contextCaptor.capture(), eventCaptor.capture());
        List<EventMessage<?>> result = eventCaptor.getValue();
        assertEquals("Event1", result.getFirst().getPayload());
        assertEquals("java.lang.String", result.getFirst().type().qualifiedName().name());
        assertTrue(contextCaptor.getValue().isCommitted());
    }

    @Test
    void publishSingleEventWithContextPublishesWithThatContext() {
        // Given
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> eventCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ProcessingContext> contextCaptor = ArgumentCaptor.forClass(ProcessingContext.class);

        // When
        StubProcessingContext context = new StubProcessingContext();
        testSubject.publish(context, "Event1");

        // Then
        verify(mockEventSink).publish(contextCaptor.capture(), eventCaptor.capture());
        List<EventMessage<?>> result = eventCaptor.getValue();
        assertEquals("Event1", result.getFirst().getPayload());
        assertEquals("java.lang.String", result.getFirst().type().qualifiedName().name());
        assertSame(context, contextCaptor.getValue());
    }


    @Test
    void publishMultipleEvents() {
        //Given
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> eventsCaptor = ArgumentCaptor.forClass(List.class);

        //When
        testSubject.publish("Event2", "Event3");

        //Then
        verify(mockEventSink).publish(any(ProcessingContext.class), eventsCaptor.capture());
        List<EventMessage<?>> result = eventsCaptor.getValue();
        assertEquals(2, result.size());
        assertEquals("Event2", result.get(0).getPayload());
        assertEquals("java.lang.String", result.getFirst().type().qualifiedName().name());
        assertEquals("Event3", result.get(1).getPayload());
        assertEquals("java.lang.String", result.get(1).type().qualifiedName().name());
    }

    @Test
    void publishMessage() {
        // when
        var payload = new TestPayload(UUID.randomUUID().toString());
        var message = new GenericEventMessage<>(new MessageType("TestPayload"), payload)
                .withMetaData(MetaData.with("key", "value"));
        testSubject.publish(message);

        // then
        verify(mockEventSink).publish(
                any(ProcessingContext.class),
                argThat((List<EventMessage<?>> msgs) -> msgs.size() == 1 && msgs.getFirst().equals(message)));
    }

    @Test
    void forProcessingContextReturnsBoundGateway() {
        StubProcessingContext boundProcessingContext = new StubProcessingContext();
        EventGateway boundTestSubject = testSubject.forProcessingContext(boundProcessingContext);
        ArgumentCaptor<ProcessingContext> contextCaptor = ArgumentCaptor.forClass(ProcessingContext.class);

        boundTestSubject.publish("Event1");

        verify(mockEventSink).publish(contextCaptor.capture(), anyList());
        assertSame(boundProcessingContext, contextCaptor.getValue());
    }

    private record TestPayload(String value) {

    }
}
/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * @author Bert Laverman
 */
class DefaultEventGatewayTest {

    private DefaultEventGateway testSubject;
    private EventBus mockEventBus;
    private MessageDispatchInterceptor mockEventMessageTransformer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        mockEventBus = mock(EventBus.class);
        mockEventMessageTransformer = mock(MessageDispatchInterceptor.class);

        when(mockEventMessageTransformer.handle(isA(EventMessage.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        testSubject = DefaultEventGateway.builder()
                .eventBus(mockEventBus)
                .dispatchInterceptors(mockEventMessageTransformer)
                .build();
    }

    @SuppressWarnings({"unchecked", "serial"})
    @Test
    void publish() {
        testSubject.publish("Event1");
        verify(mockEventBus).publish(
                argThat((GenericEventMessage msg) -> msg.getPayload().equals("Event1")));
        verify(mockEventMessageTransformer).handle(
                argThat((GenericEventMessage msg) -> msg.getPayload().equals("Event1")));

        testSubject.publish("Event2", "Event3");

        verify(mockEventBus).publish(
                argThat((GenericEventMessage msg) -> msg.getPayload().equals("Event2")));
        verify(mockEventMessageTransformer).handle(
                argThat((GenericEventMessage msg) -> msg.getPayload().equals("Event2")));
        verify(mockEventBus).publish(
                argThat((GenericEventMessage msg) -> msg.getPayload().equals("Event3")));
        verify(mockEventMessageTransformer).handle(
                argThat((GenericEventMessage msg) -> msg.getPayload().equals("Event3")));
    }

    @Test
    void publishMessage() {
        // when
        var payload = new TestPayload(UUID.randomUUID().toString());
        var message = new GenericEventMessage<>(new QualifiedName("test", "TestPayload", "0.5.0"), payload)
                .withMetaData(MetaData.with("key", "value"));
        testSubject.publish(message);

        // then
        verify(mockEventBus).publish(
                argThat((GenericEventMessage msg) -> msg.equals(message)));
        verify(mockEventMessageTransformer).handle(
                argThat((GenericEventMessage msg) -> msg.equals(message)));
    }

    private record TestPayload(String value) {
    }

}
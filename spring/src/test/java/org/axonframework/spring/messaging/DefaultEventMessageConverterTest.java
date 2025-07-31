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

package org.axonframework.spring.messaging;


import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultEventMessageConverter}.
 *
 * @author Reda.Housni-Alaoui
 */
class DefaultEventMessageConverterTest {

    private final EventMessageConverter eventMessageConverter = new DefaultEventMessageConverter();

    @Test
    void givenGenericEventMessageWhenConvertingTwiceThenResultingEventShouldBeTheSame() {
        String id = UUID.randomUUID().toString();
        MessageType name = new MessageType("event");
        EventPayload payload = new EventPayload("hello");
        Map<String, String> metaData = new HashMap<>();
        metaData.put("number", "100");
        metaData.put("string", "world");
        Instant instant = Instant.EPOCH;

        EventMessage<EventPayload> axonMessage =
                new GenericEventMessage<>(id, name, payload, metaData, instant);

        EventMessage<EventPayload> convertedAxonMessage = eventMessageConverter.convertFromInboundMessage(
                eventMessageConverter.convertToOutboundMessage(axonMessage)
        );

        assertEquals(instant, convertedAxonMessage.getTimestamp());
        assertEquals("100", convertedAxonMessage.metaData().get("number"));
        assertEquals("world", convertedAxonMessage.metaData().get("string"));
        assertEquals("hello", convertedAxonMessage.payload().name);
        assertEquals(id, convertedAxonMessage.identifier());
    }

    @Test
    void givenDomainEventMessageWhenConvertingTwiceThenResultingEventShouldBeTheSame() {
        String aggId = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        MessageType name = new MessageType("event");
        EventPayload payload = new EventPayload("hello");
        Map<String, String> metaData = new HashMap<>();
        metaData.put("number", "100");
        metaData.put("string", "world");
        Instant instant = Instant.EPOCH;

        EventMessage<EventPayload> axonMessage =
                new GenericDomainEventMessage<>("foo", aggId, 1, id, name, payload, metaData, instant);
        EventMessage<EventPayload> convertedAxonMessage = eventMessageConverter.convertFromInboundMessage(
                eventMessageConverter.convertToOutboundMessage(axonMessage)
        );

        assertInstanceOf(DomainEventMessage.class, convertedAxonMessage);

        DomainEventMessage<EventPayload> convertDomainMessage = (DomainEventMessage<EventPayload>) convertedAxonMessage;
        assertEquals(instant, convertDomainMessage.getTimestamp());
        assertEquals("100", convertDomainMessage.metaData().get("number"));
        assertEquals("world", convertDomainMessage.metaData().get("string"));
        assertEquals("hello", convertDomainMessage.payload().name);
        assertEquals(id, convertDomainMessage.identifier());
        assertEquals("foo", convertDomainMessage.getType());
        assertEquals(aggId, convertDomainMessage.getAggregateIdentifier());
        assertEquals(1, convertDomainMessage.getSequenceNumber());
    }

    private record EventPayload(String name) {

    }
}

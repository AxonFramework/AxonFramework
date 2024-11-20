/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.axonframework.messaging.QualifiedName.dottedName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created on 04/02/17.
 *
 * @author Reda.Housni-Alaoui
 */
class DefaultEventMessageConverterTest {

    private final EventMessageConverter eventMessageConverter = new DefaultEventMessageConverter();

    @Test
    void givenGenericEventMessageWhenConvertingTwiceThenResultingEventShouldBeTheSame() {
        String id = UUID.randomUUID().toString();
        QualifiedName type = dottedName("test.event");
        EventPayload payload = new EventPayload("hello");
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("number", 100);
        metaData.put("string", "world");
        Instant instant = Instant.EPOCH;

        EventMessage<EventPayload> axonMessage =
                new GenericEventMessage<>(id, type, payload, metaData, instant);

        EventMessage<EventPayload> convertedAxonMessage = eventMessageConverter.convertFromInboundMessage(
                eventMessageConverter.convertToOutboundMessage(axonMessage)
        );

        assertEquals(instant, convertedAxonMessage.getTimestamp());
        assertEquals(100, convertedAxonMessage.getMetaData().get("number"));
        assertEquals("world", convertedAxonMessage.getMetaData().get("string"));
        assertEquals("hello", convertedAxonMessage.getPayload().name);
        assertEquals(id, convertedAxonMessage.getIdentifier());
    }

    @Test
    void givenDomainEventMessageWhenConvertingTwiceThenResultingEventShouldBeTheSame() {
        String aggId = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        QualifiedName type = dottedName("test.event");
        EventPayload payload = new EventPayload("hello");
        Map<String, Object> metaData = new HashMap<>();
        metaData.put("number", 100);
        metaData.put("string", "world");
        Instant instant = Instant.EPOCH;

        EventMessage<EventPayload> axonMessage =
                new GenericDomainEventMessage<>("foo", aggId, 1, id, type, payload, metaData, instant);
        EventMessage<EventPayload> convertedAxonMessage = eventMessageConverter.convertFromInboundMessage(
                eventMessageConverter.convertToOutboundMessage(axonMessage)
        );

        assertInstanceOf(DomainEventMessage.class, convertedAxonMessage);

        DomainEventMessage<EventPayload> convertDomainMessage = (DomainEventMessage<EventPayload>) convertedAxonMessage;
        assertEquals(instant, convertDomainMessage.getTimestamp());
        assertEquals(100, convertDomainMessage.getMetaData().get("number"));
        assertEquals("world", convertDomainMessage.getMetaData().get("string"));
        assertEquals("hello", convertDomainMessage.getPayload().name);
        assertEquals(id, convertDomainMessage.getIdentifier());
        assertEquals("foo", convertDomainMessage.getType());
        assertEquals(aggId, convertDomainMessage.getAggregateIdentifier());
        assertEquals(1, convertDomainMessage.getSequenceNumber());
    }

    private record EventPayload(String name) {

    }
}

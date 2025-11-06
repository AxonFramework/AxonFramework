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

package org.axonframework.extension.spring.messaging;


import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.MessageType;
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
        Map<String, String> metadata = new HashMap<>();
        metadata.put("number", "100");
        metadata.put("string", "world");
        Instant instant = Instant.EPOCH;

        EventMessage axonMessage =
                new GenericEventMessage(id, name, payload, metadata, instant);

        EventMessage convertedAxonMessage = eventMessageConverter.convertFromInboundMessage(
                eventMessageConverter.convertToOutboundMessage(axonMessage)
        );

        assertEquals(instant, convertedAxonMessage.timestamp());
        assertEquals("100", convertedAxonMessage.metadata().get("number"));
        assertEquals("world", convertedAxonMessage.metadata().get("string"));
        assertEquals("hello", convertedAxonMessage.payloadAs(EventPayload.class).name);
        assertEquals(id, convertedAxonMessage.identifier());
    }

    private record EventPayload(String name) {

    }
}

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

import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.GenericDomainEventMessage;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link org.axonframework.messaging.eventhandling.GenericDomainEventMessage}.
 *
 * @author Allard Buijze
 */
class GenericDomainEventMessageTest {

    @Test
    void constructor() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        org.axonframework.messaging.eventhandling.DomainEventMessage message1 = new org.axonframework.messaging.eventhandling.GenericDomainEventMessage(
                "type", id, seqNo, new MessageType("event"), payload
        );
        Map<String, String> metadataMap = Collections.singletonMap("key", "value");
        Metadata metadata = Metadata.from(metadataMap);
        org.axonframework.messaging.eventhandling.DomainEventMessage message2 = new org.axonframework.messaging.eventhandling.GenericDomainEventMessage(
                "type", id, seqNo, new MessageType("event"), payload, metadata
        );
        DomainEventMessage message3 = new org.axonframework.messaging.eventhandling.GenericDomainEventMessage(
                "type", id, seqNo, new MessageType("event"), payload, metadataMap
        );

        assertSame(id, message1.getAggregateIdentifier());
        assertEquals(seqNo, message1.getSequenceNumber());
        assertSame(Metadata.emptyInstance(), message1.metadata());
        assertEquals(Object.class, message1.payload().getClass());
        assertEquals(Object.class, message1.payloadType());

        assertSame(id, message2.getAggregateIdentifier());
        assertEquals(seqNo, message2.getSequenceNumber());
        assertSame(metadata, message2.metadata());
        assertEquals(Object.class, message2.payload().getClass());
        assertEquals(Object.class, message2.payloadType());

        assertSame(id, message3.getAggregateIdentifier());
        assertEquals(seqNo, message3.getSequenceNumber());
        assertNotSame(metadataMap, message3.metadata());
        assertEquals(metadataMap, message3.metadata());
        assertEquals(Object.class, message3.payload().getClass());
        assertEquals(Object.class, message3.payloadType());

        assertNotEquals(message1.identifier(), message2.identifier());
        assertNotEquals(message1.identifier(), message3.identifier());
        assertNotEquals(message2.identifier(), message3.identifier());
    }

    @Test
    void withMetadata() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        Map<String, String> metadataMap = Collections.singletonMap("key", "value");
        Metadata metadata = Metadata.from(metadataMap);
        org.axonframework.messaging.eventhandling.GenericDomainEventMessage message = new org.axonframework.messaging.eventhandling.GenericDomainEventMessage(
                "type", id, seqNo, new MessageType("event"), payload, metadata
        );
        org.axonframework.messaging.eventhandling.GenericDomainEventMessage message1 = message.withMetadata(Metadata.emptyInstance());
        org.axonframework.messaging.eventhandling.GenericDomainEventMessage message2 = message.withMetadata(
                Metadata.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(0, message1.metadata().size());
        assertEquals(1, message2.metadata().size());
    }

    @Test
    void andMetadata() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        Map<String, String> metadataMap = Collections.singletonMap("key", "value");
        Metadata metadata = Metadata.from(metadataMap);
        org.axonframework.messaging.eventhandling.GenericDomainEventMessage message = new org.axonframework.messaging.eventhandling.GenericDomainEventMessage(
                "type", id, seqNo, new MessageType("event"), payload, metadata
        );
        org.axonframework.messaging.eventhandling.GenericDomainEventMessage message1 = message.andMetadata(Metadata.emptyInstance());
        org.axonframework.messaging.eventhandling.GenericDomainEventMessage message2 = message.andMetadata(
                Metadata.from(Collections.singletonMap("key", "otherValue")));

        assertEquals(1, message1.metadata().size());
        assertEquals("value", message1.metadata().get("key"));
        assertEquals(1, message2.metadata().size());
        assertEquals("otherValue", message2.metadata().get("key"));
    }

    @Test
    void toStringIsAsExpected() {
        String actual = new GenericDomainEventMessage(
                "AggregateType", "id1", 1, new MessageType("event"), "MyPayload"
        ).andMetadata(Metadata.with("key", "value").and("key2", "13"))
         .toString();
        assertTrue(actual.startsWith("GenericDomainEventMessage{type={event#0.0.1}, payload={MyPayload}, metadata={"),
                   "Wrong output: " + actual);
        assertTrue(actual.contains("'key'->'value'"), "Wrong output: " + actual);
        assertTrue(actual.contains("'key2'->'13'"), "Wrong output: " + actual);
        assertTrue(actual.contains("', timestamp='"), "Wrong output: " + actual);
        assertTrue(actual.contains("', aggregateIdentifier='id1'"), "Wrong output: " + actual);
        assertTrue(actual.contains("', aggregateType='AggregateType'"), "Wrong output: " + actual);
        assertTrue(actual.contains("', sequenceNumber=1"), "Wrong output: " + actual);
        assertTrue(actual.endsWith("}"), "Wrong output: " + actual);
    }
}

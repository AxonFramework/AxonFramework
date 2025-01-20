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

package org.axonframework.eventhandling;

import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericDomainEventMessage}.
 *
 * @author Allard Buijze
 */
class GenericDomainEventMessageTest {

    @Test
    void constructor() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        DomainEventMessage<Object> message1 = new GenericDomainEventMessage<>(
                "type", id, seqNo, new MessageType("event"), payload
        );
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        DomainEventMessage<Object> message2 = new GenericDomainEventMessage<>(
                "type", id, seqNo, new MessageType("event"), payload, metaData
        );
        DomainEventMessage<Object> message3 = new GenericDomainEventMessage<>(
                "type", id, seqNo, new MessageType("event"), payload, metaDataMap
        );

        assertSame(id, message1.getAggregateIdentifier());
        assertEquals(seqNo, message1.getSequenceNumber());
        assertSame(MetaData.emptyInstance(), message1.getMetaData());
        assertEquals(Object.class, message1.getPayload().getClass());
        assertEquals(Object.class, message1.getPayloadType());

        assertSame(id, message2.getAggregateIdentifier());
        assertEquals(seqNo, message2.getSequenceNumber());
        assertSame(metaData, message2.getMetaData());
        assertEquals(Object.class, message2.getPayload().getClass());
        assertEquals(Object.class, message2.getPayloadType());

        assertSame(id, message3.getAggregateIdentifier());
        assertEquals(seqNo, message3.getSequenceNumber());
        assertNotSame(metaDataMap, message3.getMetaData());
        assertEquals(metaDataMap, message3.getMetaData());
        assertEquals(Object.class, message3.getPayload().getClass());
        assertEquals(Object.class, message3.getPayloadType());

        assertNotEquals(message1.getIdentifier(), message2.getIdentifier());
        assertNotEquals(message1.getIdentifier(), message3.getIdentifier());
        assertNotEquals(message2.getIdentifier(), message3.getIdentifier());
    }

    @Test
    void withMetaData() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<>(
                "type", id, seqNo, new MessageType("event"), payload, metaData
        );
        GenericDomainEventMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericDomainEventMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    void andMetaData() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<>(
                "type", id, seqNo, new MessageType("event"), payload, metaData
        );
        GenericDomainEventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericDomainEventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }

    @Test
    void testToString() {
        String actual = new GenericDomainEventMessage<>(
                "AggregateType", "id1", 1, new MessageType("event"), "MyPayload"
        ).andMetaData(MetaData.with("key", "value").and("key2", 13))
         .toString();
        assertTrue(actual.startsWith("GenericDomainEventMessage{payload={MyPayload}, metadata={"),
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

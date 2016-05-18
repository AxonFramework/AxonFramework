/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.messaging.metadata.MetaData;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class GenericDomainEventMessageTest {

    @Test
    public void testConstructor() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        GenericDomainEventMessage<Object> message1 = new GenericDomainEventMessage<>("type", id, seqNo, payload);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message2 = new GenericDomainEventMessage<>("type", id, seqNo, payload, metaData);
        GenericDomainEventMessage<Object> message3 = new GenericDomainEventMessage<>("type", id, seqNo, payload, metaDataMap);

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

        assertFalse(message1.getIdentifier().equals(message2.getIdentifier()));
        assertFalse(message1.getIdentifier().equals(message3.getIdentifier()));
        assertFalse(message2.getIdentifier().equals(message3.getIdentifier()));
    }

    @Test
    public void testWithMetaData() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<>("type", id, seqNo, payload, metaData);
        GenericDomainEventMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericDomainEventMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    public void testAndMetaData() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<>("type", id, seqNo, payload, metaData);
        GenericDomainEventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericDomainEventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }
}

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

package org.axonframework.serializer;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.MetaData;
import org.joda.time.DateTime;
import org.junit.*;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SerializedDomainEventMessageTest {

    private SerializedObject<String> serializedPayload = new SimpleSerializedObject<String>("serialized",
                                                                                            String.class,
                                                                                            "java.lang.Object",
                                                                                            "1");
    private SerializedObject<String> serializedMetaData = new SerializedMetaData<String>("serialized",
                                                                                         String.class);

    private Object deserializedPayload = new Object();
    private MetaData deserializedMetaData = MetaData.emptyInstance();
    private Serializer serializer = mock(Serializer.class);
    private SerializedDomainEventData domainEventData = mock(SerializedDomainEventData.class);
    private final UUID id = UUID.randomUUID();
    private final long seqNo = 1L;

    @Before
    public void setUp() {
        when(domainEventData.getAggregateIdentifier()).thenReturn(id);
        when(domainEventData.getEventIdentifier()).thenReturn("eventId");
        when(domainEventData.getMetaData()).thenReturn(serializedMetaData);
        when(domainEventData.getPayload()).thenReturn(serializedPayload);
        when(domainEventData.getSequenceNumber()).thenReturn(seqNo);
        when(domainEventData.getTimestamp()).thenReturn(new DateTime());
        when(serializer.deserialize(serializedMetaData)).thenReturn(deserializedMetaData);
        when(serializer.deserialize(serializedPayload)).thenReturn(deserializedPayload);
        when(serializer.classForType(isA(SerializedType.class))).thenReturn(Object.class);
    }

    @Test
    public void testConstructor() {
        SerializedDomainEventMessage<Object> message1 = new SerializedDomainEventMessage<Object>(domainEventData,
                                                                                                 serializer);

        assertSame(id, message1.getAggregateIdentifier());
        assertEquals(seqNo, message1.getSequenceNumber());


        assertSame(MetaData.emptyInstance(), message1.getMetaData());
        assertEquals(Object.class, message1.getPayloadType());
        assertFalse(message1.isPayloadDeserialized());
        assertEquals(Object.class, message1.getPayload().getClass());
        assertTrue(message1.isPayloadDeserialized());
    }

    @Test
    public void testWithMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);
        SerializedDomainEventMessage<Object> message = new SerializedDomainEventMessage<Object>(domainEventData,
                                                                                                serializer);
        DomainEventMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        DomainEventMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(0, message1.getMetaData().size());
        assertEquals(1, message2.getMetaData().size());
    }

    @Test
    public void testAndMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);
        DomainEventMessage<Object> message = new SerializedDomainEventMessage<Object>(domainEventData, serializer);
        DomainEventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        DomainEventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }
}

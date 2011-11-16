package org.axonframework.domain;

import org.junit.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class GenericDomainEventMessageTest {

    @Test
    public void testConstructor() {
        Object payload = new Object();
        long seqNo = 0;
        AggregateIdentifier id = new UUIDAggregateIdentifier();
        GenericDomainEventMessage<Object> message1 = new GenericDomainEventMessage<Object>(id, seqNo, payload);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message2 = new GenericDomainEventMessage<Object>(id, seqNo,
                                                                                           payload, metaData);
        GenericDomainEventMessage<Object> message3 = new GenericDomainEventMessage<Object>(id, seqNo,
                                                                                           payload, metaDataMap);

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

        assertFalse(message1.getEventIdentifier().equals(message2.getEventIdentifier()));
        assertFalse(message1.getEventIdentifier().equals(message3.getEventIdentifier()));
        assertFalse(message2.getEventIdentifier().equals(message3.getEventIdentifier()));
    }

    @Test
    public void testWithMetaData() {
        Object payload = new Object();
        long seqNo = 0;
        AggregateIdentifier id = new UUIDAggregateIdentifier();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<Object>(id, seqNo,
                                                                                          payload, metaData);
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
        AggregateIdentifier id = new UUIDAggregateIdentifier();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", (Object) "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<Object>(id, seqNo,
                                                                                          payload, metaData);
        GenericDomainEventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericDomainEventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertEquals(1, message1.getMetaData().size());
        assertEquals("value", message1.getMetaData().get("key"));
        assertEquals(1, message2.getMetaData().size());
        assertEquals("otherValue", message2.getMetaData().get("key"));
    }
}

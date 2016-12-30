package org.axonframework.amqp.eventhandling;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultAMQPMessageConverterTest {

    private DefaultAMQPMessageConverter testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new DefaultAMQPMessageConverter(new XStreamSerializer());
    }

    @Test
    public void testWriteAndReadEventMessage() throws Exception {
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("SomePayload").withMetaData(MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);
        EventMessage<?> actualResult = testSubject.readAMQPMessage(amqpMessage.getBody(), amqpMessage.getProperties().getHeaders())
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(eventMessage.getIdentifier(), amqpMessage.getProperties().getHeaders().get("axon-message-id"));
        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
    }

    @Test
    public void testMessageIgnoredIfNotAxonMessageIdPresent() {
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("SomePayload").withMetaData(MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);

        Map<String, Object> headers = new HashMap<>(amqpMessage.getProperties().getHeaders());
        headers.remove("axon-message-id");
        assertFalse(testSubject.readAMQPMessage(amqpMessage.getBody(), headers).isPresent());
    }

    @Test
    public void testMessageIgnoredIfNotAxonMessageTypePresent() {
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("SomePayload").withMetaData(MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);

        Map<String, Object> headers = new HashMap<>(amqpMessage.getProperties().getHeaders());
        headers.remove("axon-message-type");
        assertFalse(testSubject.readAMQPMessage(amqpMessage.getBody(), headers).isPresent());
    }

    @Test
    public void testWriteAndReadDomainEventMessage() throws Exception {
        DomainEventMessage<?> eventMessage = new GenericDomainEventMessage<>("Stub", "1234", 1L, "Payload", MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);
        EventMessage<?> actualResult = testSubject.readAMQPMessage(amqpMessage.getBody(), amqpMessage.getProperties().getHeaders())
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(eventMessage.getIdentifier(), amqpMessage.getProperties().getHeaders().get("axon-message-id"));
        assertEquals("1234", amqpMessage.getProperties().getHeaders().get("axon-message-aggregate-id"));
        assertEquals(1L, amqpMessage.getProperties().getHeaders().get("axon-message-aggregate-seq"));

        assertTrue(actualResult instanceof DomainEventMessage);
        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
        assertEquals(eventMessage.getAggregateIdentifier(), ((DomainEventMessage)actualResult).getAggregateIdentifier());
        assertEquals(eventMessage.getType(), ((DomainEventMessage)actualResult).getType());
        assertEquals(eventMessage.getSequenceNumber(), ((DomainEventMessage)actualResult).getSequenceNumber());
    }
}

package org.axonframework.amqp.eventhandling.legacy;

import org.axonframework.amqp.eventhandling.AMQPMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import static org.junit.Assert.*;

public class JavaSerializationAMQPMessageConverterTest {

    private JavaSerializationAMQPMessageConverter testSubject;

    @Before
    public void setUp() {
        testSubject = new JavaSerializationAMQPMessageConverter(XStreamSerializer.builder().build());
    }

    @Test
    public void testWriteAndReadEventMessage() {
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("SomePayload")
                                                          .withMetaData(MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);
        EventMessage<?> actualResult = testSubject.readAMQPMessage(amqpMessage.getBody(),
                                                                   amqpMessage.getProperties().getHeaders())
                                                  .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
    }

    @Test
    public void testWriteAndReadDomainEventMessage() {
        DomainEventMessage<?> eventMessage = new GenericDomainEventMessage<>(
                "Stub", "1234", 1L, "Payload", MetaData.with("key", "value")
        );
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);
        EventMessage<?> actualResult = testSubject.readAMQPMessage(amqpMessage.getBody(),
                                                                   amqpMessage.getProperties().getHeaders())
                                                  .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertTrue(actualResult instanceof DomainEventMessage);
        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
        assertEquals(
                eventMessage.getAggregateIdentifier(), ((DomainEventMessage) actualResult).getAggregateIdentifier()
        );
        assertEquals(eventMessage.getSequenceNumber(), ((DomainEventMessage) actualResult).getSequenceNumber());
    }
}

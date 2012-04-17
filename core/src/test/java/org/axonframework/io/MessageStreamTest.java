package org.axonframework.io;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class MessageStreamTest {

    @Test
    public void testStreamEventMessage() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XStreamSerializer serializer = new XStreamSerializer();
        EventMessageWriter out = new EventMessageWriter(new DataOutputStream(baos), serializer);
        GenericEventMessage<String> message = new GenericEventMessage<String>("This is the payload",
                                                                              Collections.<String, Object>singletonMap(
                                                                                      "metaKey",
                                                                                      "MetaValue"));
        out.writeEventMessage(message);
        EventMessageReader in = new EventMessageReader(new DataInputStream(
                new ByteArrayInputStream(baos.toByteArray())), serializer);
        EventMessage<Object> serializedMessage = in.readEventMessage();

        assertEquals(message.getIdentifier(), serializedMessage.getIdentifier());
        assertEquals(message.getPayloadType(), serializedMessage.getPayloadType());
        assertEquals(message.getTimestamp(), serializedMessage.getTimestamp());
        assertEquals(message.getMetaData(), serializedMessage.getMetaData());
        assertEquals(message.getPayload(), serializedMessage.getPayload());
    }

    @Test
    public void testStreamDomainEventMessage() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XStreamSerializer serializer = new XStreamSerializer();
        EventMessageWriter out = new EventMessageWriter(new DataOutputStream(baos), serializer);
        GenericDomainEventMessage<String> message = new GenericDomainEventMessage<String>(
                "AggregateID", 1L, "This is the payload", Collections.<String, Object>singletonMap("metaKey",
                                                                                                   "MetaValue"));
        out.writeEventMessage(message);
        EventMessageReader in = new EventMessageReader(
                new DataInputStream(new ByteArrayInputStream(baos.toByteArray())), serializer);
        EventMessage<Object> serializedMessage = in.readEventMessage();
        assertTrue(serializedMessage instanceof DomainEventMessage);

        DomainEventMessage serializedDomainEventMessage = (DomainEventMessage) serializedMessage;

        assertEquals(message.getIdentifier(), serializedDomainEventMessage.getIdentifier());
        assertEquals(message.getPayloadType(), serializedDomainEventMessage.getPayloadType());
        assertEquals(message.getTimestamp(), serializedDomainEventMessage.getTimestamp());
        assertEquals(message.getMetaData(), serializedDomainEventMessage.getMetaData());
        assertEquals(message.getPayload(), serializedDomainEventMessage.getPayload());
        assertEquals(message.getAggregateIdentifier(), serializedDomainEventMessage.getAggregateIdentifier());
        assertEquals(message.getSequenceNumber(), serializedDomainEventMessage.getSequenceNumber());
    }
}

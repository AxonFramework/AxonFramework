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

package org.axonframework.eventhandling.io;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Allard Buijze
 */
public class MessageStreamTest {

    @Test
    public void testStreamEventMessage() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XStreamSerializer serializer = new XStreamSerializer();
        EventMessageWriter out = new EventMessageWriter(new DataOutputStream(baos), serializer);
        GenericEventMessage<String> message = new GenericEventMessage<>("This is the payload",
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
        GenericDomainEventMessage<String> message = new GenericDomainEventMessage<>(
                "type", "AggregateID", 1L, "This is the payload", Collections.<String, Object>singletonMap("metaKey",
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

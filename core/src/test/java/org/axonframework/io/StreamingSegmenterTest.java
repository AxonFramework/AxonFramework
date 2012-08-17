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

package org.axonframework.io;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.eventhandling.io.EventMessageReader;
import org.axonframework.eventhandling.io.EventMessageWriter;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.*;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class StreamingSegmenterTest {

    private StreamingSegmenter testSubject;
    private Serializer serializer;

    @Before
    public void setUp() throws Exception {
        serializer = new XStreamSerializer();
        testSubject = new StreamingSegmenter(serializer);
    }

    @Test
    public void testWriteSingleMessage() throws IOException {
        final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        EventMessageWriter writer = new EventMessageWriter(new DataOutputStream(bytesOut), serializer);

        writer.writeEventMessage(new GenericDomainEventMessage<String>("aggregateId", 12L, "Payload"));

        final byte[] bytes = bytesOut.toByteArray();

        assertEquals(0, testSubject.getReadyMessageCount());
        testSubject.write(bytes, 0, bytes.length - 1);
        assertEquals(0, testSubject.getReadyMessageCount());
        testSubject.write(bytes[bytes.length - 1]);
        assertEquals(1, testSubject.getReadyMessageCount());

        // write another message
        testSubject.write(bytes);

        InputStream bytesWritten = testSubject.getByteInputStream();
        EventMessageReader reader = new EventMessageReader(new DataInputStream(bytesWritten),serializer );
        EventMessage message = reader.readEventMessage();
        assertTrue(message instanceof DomainEventMessage);
        assertEquals("aggregateId", ((DomainEventMessage) message).getAggregateIdentifier());
        assertEquals("Payload", message.getPayload());

        assertEquals(1, testSubject.getReadyMessageCount());
    }

    @Test
    public void testWriteBatchOfMessages() throws IOException {
        final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        EventMessageWriter writer = new EventMessageWriter(new DataOutputStream(bytesOut), serializer);

        writer.writeEventMessage(new GenericDomainEventMessage<String>("aggregateId", 12L, "Payload"));

        final byte[] bytes = bytesOut.toByteArray();

        testSubject.write(new byte[]{ControlMessage.BATCH_START.getTypeByte(), 0, 2});
        assertEquals(0, testSubject.getReadyMessageCount());
        testSubject.write(bytes, 0, bytes.length - 1);
        assertEquals(0, testSubject.getReadyMessageCount());
        testSubject.write(bytes[bytes.length - 1]);
        assertEquals(0, testSubject.getReadyMessageCount());

        // write another message
        testSubject.write(bytes);
        assertEquals(2, testSubject.getReadyMessageCount());

        InputStream bytesWritten = testSubject.getByteInputStream();
        EventMessageReader reader = new EventMessageReader(new DataInputStream(bytesWritten),serializer );
        EventMessage message1 = reader.readEventMessage();
        assertTrue(message1 instanceof DomainEventMessage);
        assertEquals("aggregateId", ((DomainEventMessage) message1).getAggregateIdentifier());
        assertEquals("Payload", message1.getPayload());

        EventMessage message2 = reader.readEventMessage();
        assertTrue(message2 instanceof DomainEventMessage);
        assertEquals("aggregateId", ((DomainEventMessage) message2).getAggregateIdentifier());
        assertEquals("Payload", message2.getPayload());

        assertNull(reader.readEventMessage());

        assertEquals(0, testSubject.getReadyMessageCount());
    }

    @Test
    public void testConvertToUnsignedShort() {
        int number = 0xFF;
        byte byteNumber = (byte) number;
        assertEquals(-1, byteNumber);
    }
}

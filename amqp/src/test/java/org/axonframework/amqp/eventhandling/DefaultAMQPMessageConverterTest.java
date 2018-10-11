/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.amqp.eventhandling;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.Headers;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
public class DefaultAMQPMessageConverterTest {

    private DefaultAMQPMessageConverter testSubject;

    @Before
    public void setUp() {
        testSubject = DefaultAMQPMessageConverter.builder()
                                                 .serializer(XStreamSerializer.builder().build())
                                                 .build();
    }

    @Test
    public void testWriteAndReadEventMessage() {
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("SomePayload")
                                                          .withMetaData(MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);
        EventMessage<?> actualResult = testSubject.readAMQPMessage(
                amqpMessage.getBody(), amqpMessage.getProperties().getHeaders()
        ).orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(eventMessage.getIdentifier(), amqpMessage.getProperties().getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
    }

    @Test
    public void testMessageIgnoredIfNotAxonMessageIdPresent() {
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("SomePayload")
                                                          .withMetaData(MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);

        Map<String, Object> headers = new HashMap<>(amqpMessage.getProperties().getHeaders());
        headers.remove(Headers.MESSAGE_ID);
        assertFalse(testSubject.readAMQPMessage(amqpMessage.getBody(), headers).isPresent());
    }

    @Test
    public void testMessageIgnoredIfNotAxonMessageTypePresent() {
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage("SomePayload")
                                                          .withMetaData(MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);

        Map<String, Object> headers = new HashMap<>(amqpMessage.getProperties().getHeaders());
        headers.remove(Headers.MESSAGE_TYPE);
        assertFalse(testSubject.readAMQPMessage(amqpMessage.getBody(), headers).isPresent());
    }

    @Test
    public void testWriteAndReadDomainEventMessage() {
        DomainEventMessage<?> eventMessage =
                new GenericDomainEventMessage<>("Stub", "1234", 1L, "Payload", MetaData.with("key", "value"));
        AMQPMessage amqpMessage = testSubject.createAMQPMessage(eventMessage);
        EventMessage<?> actualResult = testSubject.readAMQPMessage(
                amqpMessage.getBody(), amqpMessage.getProperties().getHeaders()
        ).orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(eventMessage.getIdentifier(), amqpMessage.getProperties().getHeaders().get(Headers.MESSAGE_ID));
        assertEquals("1234", amqpMessage.getProperties().getHeaders().get(Headers.AGGREGATE_ID));
        assertEquals(1L, amqpMessage.getProperties().getHeaders().get(Headers.AGGREGATE_SEQ));

        assertTrue(actualResult instanceof DomainEventMessage);
        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
        assertEquals(eventMessage.getAggregateIdentifier(),
                     ((DomainEventMessage) actualResult).getAggregateIdentifier());
        assertEquals(eventMessage.getType(), ((DomainEventMessage) actualResult).getType());
        assertEquals(eventMessage.getSequenceNumber(), ((DomainEventMessage) actualResult).getSequenceNumber());
    }
}

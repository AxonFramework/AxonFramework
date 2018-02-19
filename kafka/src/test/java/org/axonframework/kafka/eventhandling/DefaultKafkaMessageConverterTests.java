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

package org.axonframework.kafka.eventhandling;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.Headers;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.FixedValueRevisionResolver;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.axonframework.kafka.eventhandling.HeaderUtils.asLong;
import static org.axonframework.kafka.eventhandling.HeaderUtils.asString;
import static org.junit.Assert.*;

/**
 * @author Nakul Mishra
 * @since 3.0
 */
public class DefaultKafkaMessageConverterTests {

    private static final String SOME_TOPIC = "topicFoo";
    private static final int SOME_OFFSET = 0;
    private static final int SOME_PARTITION = 0;
    private static final String SOME_AGGREGATE_IDENTIFIER = "1234";

    private DefaultKafkaMessageConverter testSubject;

    @Before
    public void setUp() {
        testSubject = new DefaultKafkaMessageConverter(new XStreamSerializer(new FixedValueRevisionResolver("stub-revision")));
    }

    @Test
    public void testWriteAndRead_EventMessage() {
        EventMessage<?> eventMessage = eventMessage();
        ProducerRecord<String, byte[]> senderMessage = senderMessage(eventMessage);
        EventMessage<?> actualResult = receiverMessage(senderMessage)
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEventMessage(eventMessage, senderMessage.headers(), actualResult);
        assertEquals("stub-revision", asString(senderMessage.headers().lastHeader(Headers.MESSAGE_REVISION).value()));
    }

    @Test
    public void testWriteAndRead_EventMessageNullRevision() {
        testSubject = new DefaultKafkaMessageConverter(new XStreamSerializer());
        EventMessage<?> eventMessage = eventMessage();
        ProducerRecord<String, byte[]> senderMessage = senderMessage(eventMessage);
        EventMessage<?> actualResult = receiverMessage(senderMessage)
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEventMessage(eventMessage, senderMessage.headers(), actualResult);
        assertNull(asString(senderMessage.headers().lastHeader(Headers.MESSAGE_REVISION).value()));
    }

    @Test
    public void testWriteAndRead_DomainEventMessage() {
        DomainEventMessage<?> domainMessage = domainMessage();
        ProducerRecord<String, byte[]> senderMessage = senderMessage(domainMessage);
        EventMessage<?> actualResult = receiverMessage(senderMessage)
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        org.apache.kafka.common.header.Headers headers = senderMessage.headers();
        assertTrue(actualResult instanceof DomainEventMessage);
        assertEquals(SOME_AGGREGATE_IDENTIFIER, asString(headers.lastHeader(Headers.AGGREGATE_ID).value()));
        assertEquals(1L, asLong(headers.lastHeader(Headers.AGGREGATE_SEQ).value()));
        assertEventMessage(domainMessage, headers, actualResult);
        assertDomainMessage(domainMessage, (DomainEventMessage) actualResult);
    }

    @Test
    public void testReadMessage_WithoutMessageId() {
        EventMessage<?> eventMessage = eventMessage();
        ProducerRecord<String, byte[]> senderMessage = senderMessage(eventMessage);
        senderMessage.headers().remove(Headers.MESSAGE_ID);
        assertFalse(receiverMessage(senderMessage).isPresent());
    }

    @Test
    public void testReadMessage_WithoutMessageType() {
        EventMessage<?> eventMessage = eventMessage();
        ProducerRecord<String, byte[]> senderMessage = senderMessage(eventMessage);
        senderMessage.headers().remove(Headers.MESSAGE_TYPE);
        assertFalse(receiverMessage(senderMessage).isPresent());
    }

    @Test
    public void testWriteMessage_RoutingKey() {
        EventMessage<?> eventMessage = eventMessage();
        ProducerRecord<String, byte[]> senderMessage = senderMessage(eventMessage);
        assertNull(senderMessage.key());
    }

    @Test
    public void testWriteMessage_RoutingKeyWithAggregateId() {
        DomainEventMessage<?> domainMessages = domainMessage();
        ProducerRecord<String, byte[]> senderMessage = senderMessage(domainMessages);
        assertEquals(domainMessages.getAggregateIdentifier(), senderMessage.key());
    }

    private static void assertEventMessage(EventMessage<?> eventMessage, org.apache.kafka.common.header.Headers headers, EventMessage<?> actualResult) {
        assertEquals(eventMessage.getIdentifier(), axonMessageId(headers));
        assertEquals(eventMessage.getIdentifier(), actualResult.getIdentifier());
        assertEquals(eventMessage.getMetaData(), actualResult.getMetaData());
        assertEquals(eventMessage.getPayload(), actualResult.getPayload());
        assertEquals(eventMessage.getPayloadType(), actualResult.getPayloadType());
        assertEquals(eventMessage.getTimestamp(), actualResult.getTimestamp());
    }

    private static void assertDomainMessage(DomainEventMessage<?> domainMessage, DomainEventMessage actualResult) {
        assertEquals(domainMessage.getAggregateIdentifier(), actualResult.getAggregateIdentifier());
        assertEquals(domainMessage.getType(), actualResult.getType());
        assertEquals(domainMessage.getSequenceNumber(), actualResult.getSequenceNumber());
    }

    private static String axonMessageId(org.apache.kafka.common.header.Headers headers) {
        return asString(headers.lastHeader(Headers.MESSAGE_ID).value());
    }

    private GenericDomainEventMessage<String> domainMessage() {
        return new GenericDomainEventMessage<>("Stub", SOME_AGGREGATE_IDENTIFIER, 1L, "Payload", MetaData.with("key", "value"));
    }

    private EventMessage<Object> eventMessage() {
        return GenericEventMessage.asEventMessage("SomePayload").withMetaData(MetaData.with("key", "value"));
    }

    private ProducerRecord<String, byte[]> senderMessage(EventMessage<?> eventMessage) {
        return testSubject.createKafkaMessage(eventMessage, SOME_TOPIC);
    }

    private Optional<EventMessage<?>> receiverMessage(ProducerRecord<String, byte[]> senderMessage) {
        return testSubject.readKafkaMessage(toReceiverRecord(senderMessage));
    }

    private ConsumerRecord<String, byte[]> toReceiverRecord(ProducerRecord<String, byte[]> message) {
        ConsumerRecord<String, byte[]> receiverRecord = new ConsumerRecord<>(SOME_TOPIC, SOME_PARTITION, SOME_OFFSET,
                message.key(), message.value());
        message.headers().forEach(header -> receiverRecord.headers().add(header));
        return receiverRecord;
    }
}

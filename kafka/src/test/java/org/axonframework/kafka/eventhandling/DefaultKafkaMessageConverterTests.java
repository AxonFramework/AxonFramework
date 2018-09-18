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
import org.apache.kafka.common.header.Headers;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.FixedValueRevisionResolver;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import static org.apache.kafka.clients.consumer.ConsumerRecord.NULL_SIZE;
import static org.apache.kafka.common.record.RecordBatch.NO_TIMESTAMP;
import static org.apache.kafka.common.record.TimestampType.NO_TIMESTAMP_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.kafka.eventhandling.HeaderAssertUtils.assertDomainHeaders;
import static org.axonframework.kafka.eventhandling.HeaderAssertUtils.assertEventHeaders;
import static org.axonframework.kafka.eventhandling.HeaderUtils.byteMapper;
import static org.axonframework.kafka.eventhandling.HeaderUtils.toHeaders;
import static org.axonframework.kafka.eventhandling.HeaderUtils.valueAsString;
import static org.axonframework.messaging.Headers.MESSAGE_ID;
import static org.axonframework.messaging.Headers.MESSAGE_REVISION;
import static org.axonframework.messaging.Headers.MESSAGE_TYPE;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultKafkaMessageConverter}.
 *
 * @author Nakul Mishra
 */
public class DefaultKafkaMessageConverterTests {

    private static final String SOME_TOPIC = "topicFoo";
    private static final int SOME_OFFSET = 0;
    private static final int SOME_PARTITION = 0;
    private static final String SOME_AGGREGATE_IDENTIFIER = "1234";

    private DefaultKafkaMessageConverter testSubject;
    private XStreamSerializer serializer;

    @Before
    public void setUp() {
        serializer = new XStreamSerializer(new FixedValueRevisionResolver("stub-revision"));
        testSubject = new DefaultKafkaMessageConverter(serializer);
    }

    @Test
    public void testKafkaKeyGeneration_EventMessage_ShouldBeNull() {
        ProducerRecord<String, byte[]> evt = testSubject.createKafkaMessage(eventMessage(), SOME_TOPIC);

        assertThat(evt.key()).isNull();
    }

    @Test
    public void testKafkaKeyGeneration_DomainMessage_ShouldBeAggregateIdentifier() {
        ProducerRecord<String, byte[]> domainEvt = testSubject.createKafkaMessage(domainMessage(), SOME_TOPIC);

        assertThat(domainEvt.key()).isEqualTo((domainMessage().getAggregateIdentifier()));
    }

    @Test
    public void testWriting_EventMessageAsKafkaMessage_ShouldAppendEventHeaders() {
        EventMessage<?> expected = eventMessage();
        ProducerRecord<String, byte[]> senderMessage = testSubject.createKafkaMessage(expected, SOME_TOPIC);
        SerializedObject<byte[]> serializedObject = expected.serializePayload(serializer, byte[].class);

        assertEventHeaders("key", expected, serializedObject, senderMessage.headers());
    }

    @Test
    public void testWriting_DomainMessageAsKafkaMessage_ShouldAppendDomainHeaders() {
        GenericDomainEventMessage<String> expected = domainMessage();
        ProducerRecord<String, byte[]> senderMessage = testSubject.createKafkaMessage(expected, SOME_TOPIC);

        assertDomainHeaders(expected, senderMessage.headers());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReadingMessage_WhenKafkaReturnNullHeaders_ShouldReturnEmptyMessage() {
        ConsumerRecord source = mock(ConsumerRecord.class);
        when(source.headers()).thenReturn(null);

        assertThat(testSubject.readKafkaMessage(source).isPresent()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReadingMessage_MissingAxonHeader_ShouldReturnEmptyMessage() {
        ConsumerRecord msgWithoutHeaders = new ConsumerRecord("foo", 0, 0, "abc", 1);

        assertThat(testSubject.readKafkaMessage(msgWithoutHeaders).isPresent()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReadingMessage_WithoutId_ShouldReturnEmptyMessage() {
        EventMessage<?> event = eventMessage();
        ProducerRecord<String, byte[]> msg = testSubject.createKafkaMessage(event, SOME_TOPIC);
        msg.headers().remove(MESSAGE_ID);

        assertThat(testSubject.readKafkaMessage(toReceiverRecord(msg)).isPresent()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReadingMessage_WithoutType_ShouldReturnEmptyMessage() {
        EventMessage<?> event = eventMessage();
        ProducerRecord<String, byte[]> msg = testSubject.createKafkaMessage(event, SOME_TOPIC);
        msg.headers().remove(MESSAGE_TYPE);

        assertThat(testSubject.readKafkaMessage(toReceiverRecord(msg)).isPresent()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReadingMessage_PayloadDifferentThanByte_ShouldReturnEmptyMessage() {
        EventMessage<Object> eventMessage = eventMessage();
        SerializedObject serializedObject = mock(SerializedObject.class);
        when(serializedObject.getType()).thenReturn(new SimpleSerializedType("foo", null));
        Headers headers = toHeaders(eventMessage, serializedObject, byteMapper());
        ConsumerRecord payloadDifferentThanByte = new ConsumerRecord(
                "foo", 0, 0, NO_TIMESTAMP, NO_TIMESTAMP_TYPE,
                -1L, NULL_SIZE, NULL_SIZE, 1, "123", headers
        );

        assertThat(testSubject.readKafkaMessage(payloadDifferentThanByte).isPresent()).isFalse();
    }

    @Test
    public void testWriting_EventMessage_ShouldBeReadAsEventMessage() {
        EventMessage<?> expected = eventMessage();
        ProducerRecord<String, byte[]> senderMessage = testSubject.createKafkaMessage(expected, SOME_TOPIC);
        EventMessage<?> actual = receiverMessage(senderMessage);

        assertEventMessage(actual, expected);
    }

    @Test
    public void testWriting_EventMessageWithNullRevision_ShouldWriteRevisionAsNull() {
        testSubject = new DefaultKafkaMessageConverter(new XStreamSerializer());
        EventMessage<?> eventMessage = eventMessage();
        ProducerRecord<String, byte[]> senderMessage = testSubject.createKafkaMessage(eventMessage, SOME_TOPIC);

        assertThat(valueAsString(senderMessage.headers(), MESSAGE_REVISION)).isNull();
    }

    @Test
    public void testWriting_DomainEventMessage_ShouldBeReadAsDomainMessage() {
        DomainEventMessage<?> expected = domainMessage();
        ProducerRecord<String, byte[]> senderMessage = testSubject.createKafkaMessage(expected, SOME_TOPIC);
        EventMessage<?> actual = receiverMessage(senderMessage);

        assertEventMessage(actual, expected);
        assertDomainMessage((DomainEventMessage<?>) actual, expected);
    }

    private void assertDomainMessage(DomainEventMessage<?> actual, DomainEventMessage<?> expected) {
        assertThat(actual.getAggregateIdentifier()).isEqualTo(expected.getAggregateIdentifier());
        assertThat(actual.getSequenceNumber()).isEqualTo(expected.getSequenceNumber());
        assertThat(actual.getType()).isEqualTo(expected.getType());
    }

    private static void assertEventMessage(EventMessage<?> actual, EventMessage<?> expected) {
        assertThat(actual.getIdentifier()).isEqualTo(expected.getIdentifier());
        assertEquals(actual.getPayloadType(), (expected.getPayloadType()));
        assertThat(actual.getMetaData()).isEqualTo(expected.getMetaData());
        assertThat(actual.getPayload()).isEqualTo(expected.getPayload());
        assertThat(actual.getTimestamp()).isEqualTo(expected.getTimestamp());
    }

    private static EventMessage<Object> eventMessage() {
        return asEventMessage("SomePayload").withMetaData(MetaData.with("key", "value"));
    }

    private static GenericDomainEventMessage<String> domainMessage() {
        return new GenericDomainEventMessage<>("Stub",
                                               SOME_AGGREGATE_IDENTIFIER,
                                               1L,
                                               "Payload",
                                               MetaData.with("key", "value"));
    }

    private EventMessage<?> receiverMessage(ProducerRecord<String, byte[]> senderMessage) {
        return testSubject.readKafkaMessage(
                toReceiverRecord(senderMessage)).orElseThrow(() -> new AssertionError("Expected valid message")
        );
    }

    private static ConsumerRecord<String, byte[]> toReceiverRecord(ProducerRecord<String, byte[]> message) {
        ConsumerRecord<String, byte[]> receiverRecord = new ConsumerRecord<>(
                SOME_TOPIC, SOME_PARTITION, SOME_OFFSET, message.key(), message.value()
        );
        message.headers().forEach(header -> receiverRecord.headers().add(header));
        return receiverRecord;
    }
}

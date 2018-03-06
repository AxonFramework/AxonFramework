/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.Headers;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.axonframework.kafka.eventhandling.HeaderUtils.*;
import static org.axonframework.serialization.MessageSerializer.serializePayload;

/**
 * Converts {@link EventMessage} to {@link ProducerRecord} and {@link ConsumerRecord} to {@link EventMessage}.
 * <p>
 * During conversion it passes all meta-data entries as toHeader (with 'axon-metadata-' prefix) to {@link
 * org.apache.kafka.common.header.Headers}.
 * Other message-specific attributes are also added as meta data.  The message payload is serialized using the
 * configured serializer and passed
 * as the message body.
 * <p>
 * This implementation will suffice in most cases.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public class DefaultKafkaMessageConverter implements KafkaMessageConverter<String, byte[]> {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConverter.class);
    private final Serializer serializer;
    private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;
    private final BiFunction<String, Object, RecordHeader> headerValueMapper;

    /**
     * Initializes the KafkaMessageConverter with the given {@code serializer}.
     *
     * @param serializer The serializer to serialize the Event Message's payload with
     */
    public DefaultKafkaMessageConverter(Serializer serializer) {
        this(serializer, new SequentialPerAggregatePolicy(),
             (key, value) -> value instanceof byte[] ? new RecordHeader(key, (byte[]) value) : new RecordHeader(key,
                                                                                                                value.toString()
                                                                                                                     .getBytes()));
    }

    /**
     * Initializes the KafkaMessageConverter with the given {@code serializer}, {@code sequencingPolicy} and
     * {@code objectMapper}
     *
     * @param serializer        The serializer to serialize the Event Message's payload and Meta Data with
     * @param sequencingPolicy  The policy to generate the key of the {@link ProducerRecord}, if one exists (null is
     *                          allowed)
     * @param headerValueMapper The Function to generate how the values will be sent to kafka headers.
     */
    public DefaultKafkaMessageConverter(Serializer serializer,
                                        SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                                        BiFunction<String, Object, RecordHeader> headerValueMapper) {
        Assert.notNull(serializer, () -> "Serializer may not be null");
        Assert.notNull(sequencingPolicy, () -> "SequencingPolicy may not be null");
        Assert.notNull(headerValueMapper, () -> "HeaderValueMapper may not be null");
        this.serializer = serializer;
        this.sequencingPolicy = sequencingPolicy;
        this.headerValueMapper = headerValueMapper;
    }

    @Override
    public ProducerRecord<String, byte[]> createKafkaMessage(EventMessage<?> eventMessage, String topic) {
        SerializedObject<byte[]> serializedObject = serializePayload(eventMessage, serializer, byte[].class);
        byte[] payload = serializedObject.getData();
        return new ProducerRecord<>(topic,
                                    null,
                                    null,
                                    key(eventMessage),
                                    payload,
                                    toHeader(eventMessage, serializedObject));
    }

    private String key(EventMessage<?> eventMessage) {
        Object identifier = sequencingPolicy.getSequenceIdentifierFor(eventMessage);
        return identifier != null ? identifier.toString() : null;
    }

    protected org.apache.kafka.common.header.Headers toHeader(EventMessage<?> eventMessage,
                                                              SerializedObject<byte[]> serializedObject) {
        RecordHeaders headers = new RecordHeaders();
        addAxonHeaders(eventMessage, serializedObject, headers);
        return headers;
    }

    private void addAxonHeaders(EventMessage<?> eventMessage, SerializedObject<byte[]> serializedObject,
                                org.apache.kafka.common.header.Headers target) {
        eventMessage.getMetaData().forEach((k, v) -> target
                .add(headerValueMapper.apply(Headers.MESSAGE_METADATA + "-" + k, v)));
        Headers.defaultHeaders(eventMessage, serializedObject).forEach((k, v) -> addBytes(target, k, v));
        if (eventMessage instanceof DomainEventMessage) {
            Headers.domainHeaders((DomainEventMessage) eventMessage).forEach((k, v) -> addBytes(target, k, v));
        }
    }

    @Override
    public Optional<EventMessage<?>> readKafkaMessage(ConsumerRecord<String, byte[]> consumerRecord) {
        org.apache.kafka.common.header.Headers headers = consumerRecord.headers();
        try {
            if (!keys(headers).containsAll(Arrays.asList(Headers.MESSAGE_ID, Headers.MESSAGE_TYPE))) {
                return Optional.empty();
            }

            byte[] messageBody = consumerRecord.value();
            SimpleSerializedObject<byte[]> serializedMessage = new SimpleSerializedObject<>(messageBody, byte[].class,
                                                                                            asString(headers.lastHeader(
                                                                                                    Headers.MESSAGE_TYPE)
                                                                                                            .value()),
                                                                                            Objects.toString(asString(
                                                                                                    headers.lastHeader(
                                                                                                            Headers.MESSAGE_REVISION)
                                                                                                           .value()),
                                                                                                             null));
            SerializedMessage<?> message = new SerializedMessage<>(asString(headers.lastHeader(Headers.MESSAGE_ID).value()),
                                                                   new LazyDeserializingObject<>(serializedMessage,
                                                                                                 serializer),
                                                                   new LazyDeserializingObject<>(MetaData.from(
                                                                           extractAxonMetadata(headers))));
            long timestamp = asLong(headers.lastHeader(Headers.MESSAGE_TIMESTAMP).value());

            return headers.lastHeader(Headers.AGGREGATE_ID) != null ? domainEvent(headers, message, timestamp) : event(
                    message,
                    timestamp);
        } catch (Exception e) {
            logger.error("Error converting message from kafka to axon {}", e);
        }

        return Optional.empty();
    }

    private Optional<EventMessage<?>> domainEvent(org.apache.kafka.common.header.Headers headers,
                                                  SerializedMessage<?> message, long timestamp) {
        return Optional.of(new GenericDomainEventMessage<>(asString(headers.lastHeader(Headers.AGGREGATE_TYPE).value()),
                                                           asString(headers.lastHeader(Headers.AGGREGATE_ID).value()),
                                                           asLong(headers.lastHeader(Headers.AGGREGATE_SEQ).value()),
                                                           message, () -> Instant.ofEpochMilli(timestamp)));
    }

    private Optional<EventMessage<?>> event(SerializedMessage<?> message, long timestamp) {
        return Optional.of(new GenericEventMessage<>(message, () -> Instant.ofEpochMilli(timestamp)));
    }
}

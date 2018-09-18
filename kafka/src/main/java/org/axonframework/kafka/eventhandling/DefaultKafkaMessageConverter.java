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
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.axonframework.kafka.eventhandling.HeaderUtils.*;
import static org.axonframework.messaging.Headers.*;

/**
 * Converts: {@link EventMessage} to {@link ProducerRecord kafkaMessage} and {@link ConsumerRecord message read from
 * Kafka} back
 * to {@link EventMessage} (if possible).
 * <p>
 * During conversion it passes all meta-data entries with {@code 'axon-metadata-'} prefix to {@link Headers}. Other
 * message-specific attributes are added as metadata. The payload is serialized using the
 * configured {@link Serializer} and passed as the message body.
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
     * @param serializer The serializer to serialize the Event Message's payload with.
     */
    public DefaultKafkaMessageConverter(Serializer serializer) {
        this(serializer, SequentialPerAggregatePolicy.instance(), byteMapper());
    }

    /**
     * Initializes the KafkaMessageConverter with the given {@code serializer}, {@code sequencingPolicy} and
     * {@code objectMapper}.
     *
     * @param serializer        The serializer to serialize the Event Message's payload and Meta Data with
     * @param sequencingPolicy  The policy to generate the key of the {@link ProducerRecord}.
     * @param headerValueMapper The Function for mapping values to Kafka headers.
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
        SerializedObject<byte[]> serializedObject = eventMessage.serializePayload(serializer, byte[].class);
        byte[] payload = serializedObject.getData();
        return new ProducerRecord<>(topic,
                                    null,
                                    null,
                                    key(eventMessage),
                                    payload,
                                    toHeaders(eventMessage, serializedObject, headerValueMapper));
    }

    private String key(EventMessage<?> eventMessage) {
        Object identifier = sequencingPolicy.getSequenceIdentifierFor(eventMessage);
        return identifier != null ? identifier.toString() : null;
    }

    @Override
    public Optional<EventMessage<?>> readKafkaMessage(ConsumerRecord<String, byte[]> consumerRecord) {
        try {
            Headers headers = consumerRecord.headers();
            if (isAxonMessage(headers)) {
                byte[] messageBody = consumerRecord.value();
                SerializedMessage<?> message = extractSerializedMessage(headers, messageBody);
                return buildMessage(headers, message);
            }
        } catch (Exception e) {
            logger.trace("Error converting {} to axon", consumerRecord, e);
        }

        return Optional.empty();
    }

    private Optional<EventMessage<?>> buildMessage(Headers headers, SerializedMessage<?> message) {
        long timestamp = valueAsLong(headers, MESSAGE_TIMESTAMP);
        return headers.lastHeader(AGGREGATE_ID) != null ?
                domainEvent(headers, message, timestamp) :
                event(message, timestamp);
    }

    private SerializedMessage<?> extractSerializedMessage(Headers headers, byte[] messageBody) {
        SimpleSerializedObject<byte[]> serializedObject = new SimpleSerializedObject<>(
                messageBody,
                byte[].class,
                valueAsString(headers, MESSAGE_TYPE),
                valueAsString(headers, MESSAGE_REVISION, null)
        );
        return new SerializedMessage<>(
                valueAsString(headers, MESSAGE_ID),
                new LazyDeserializingObject<>(serializedObject, serializer),
                new LazyDeserializingObject<>(MetaData.from(extractAxonMetadata(headers))));
    }

    private boolean isAxonMessage(Headers headers) {
        return keys(headers).containsAll(Arrays.asList(MESSAGE_ID, MESSAGE_TYPE));
    }

    private Optional<EventMessage<?>> domainEvent(Headers headers,
                                                  SerializedMessage<?> message, long timestamp) {
        return Optional.of(new GenericDomainEventMessage<>(valueAsString(headers, AGGREGATE_TYPE),
                                                           valueAsString(headers, AGGREGATE_ID),
                                                           valueAsLong(headers, AGGREGATE_SEQ),
                                                           message, () -> Instant.ofEpochMilli(timestamp)));
    }

    private Optional<EventMessage<?>> event(SerializedMessage<?> message, long timestamp) {
        return Optional.of(new GenericEventMessage<>(message, () -> Instant.ofEpochMilli(timestamp)));
    }
}

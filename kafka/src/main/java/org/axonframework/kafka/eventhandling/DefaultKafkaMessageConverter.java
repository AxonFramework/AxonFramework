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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
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
import java.util.Optional;
import java.util.function.BiFunction;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.kafka.eventhandling.HeaderUtils.*;
import static org.axonframework.messaging.Headers.*;

/**
 * Converts: {@link EventMessage} to {@link ProducerRecord kafkaMessage} and {@link ConsumerRecord message read from
 * Kafka} back to {@link EventMessage} (if possible).
 * <p>
 * During conversion it passes all meta-data entries with {@code 'axon-metadata-'} prefix to {@link Headers}. Other
 * message-specific attributes are added as metadata. The payload is serialized using the configured {@link Serializer}
 * and passed as the message body.
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
     * Instantiate a {@link DefaultKafkaMessageConverter} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link Serializer} is not {@code null} and will throw an {@link AxonConfigurationException}
     * if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DefaultKafkaMessageConverter} instance
     */
    protected DefaultKafkaMessageConverter(Builder builder) {
        builder.validate();
        this.serializer = builder.serializer;
        this.sequencingPolicy = builder.sequencingPolicy;
        this.headerValueMapper = builder.headerValueMapper;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultKafkaMessageConverter}.
     * <p>
     * The {@link SequencingPolicy} is defaulted to an {@link SequentialPerAggregatePolicy}, and the
     * {@code headerValueMapper} is defaulted to the {@link HeaderUtils#byteMapper()} function.
     * The {@link Serializer} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link DefaultKafkaMessageConverter}
     */
    public static Builder builder() {
        return new Builder();
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

    /**
     * Builder class to instantiate a {@link DefaultKafkaMessageConverter}.
     * <p>
     * The {@link SequencingPolicy} is defaulted to an {@link SequentialPerAggregatePolicy}, and the
     * {@code headerValueMapper} is defaulted to the {@link HeaderUtils#byteMapper()} function.
     * The {@link Serializer} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder {

        private Serializer serializer;
        private SequencingPolicy<? super EventMessage<?>> sequencingPolicy = SequentialPerAggregatePolicy.instance();
        private BiFunction<String, Object, RecordHeader> headerValueMapper = byteMapper();

        /**
         * Sets the serializer to serialize the Event Message's payload with.
         *
         * @param serializer The serializer to serialize the Event Message's payload with
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the {@link SequencingPolicy}, with a generic of being a super of {@link EventMessage}, used to generate
         * the key for the {@link ProducerRecord}. Defaults to a {@link SequentialPerAggregatePolicy} instance.
         *
         * @param sequencingPolicy a {@link SequencingPolicy} used to generate the key for the {@link ProducerRecord}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder sequencingPolicy(SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
            assertNonNull(sequencingPolicy, "SequencingPolicy may not be null");
            this.sequencingPolicy = sequencingPolicy;
            return this;
        }

        /**
         * Sets the {@code headerValueMapper}, a {@link BiFunction} of {@link String}, {@link Object} and
         * {@link RecordHeader}, used for mapping values to Kafka headers. Defaults to the
         * {@link HeaderUtils#byteMapper()} function.
         *
         * @param headerValueMapper a {@link BiFunction} of {@link String}, {@link Object} and {@link RecordHeader},
         *                          used for mapping values to Kafka headers
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder headerValueMapper(BiFunction<String, Object, RecordHeader> headerValueMapper) {
            assertNonNull(headerValueMapper, "{} may not be null");
            this.headerValueMapper = headerValueMapper;
            return this;
        }

        /**
         * Initializes a {@link DefaultKafkaMessageConverter} as specified through this Builder.
         *
         * @return a {@link DefaultKafkaMessageConverter} as specified through this Builder
         */
        public DefaultKafkaMessageConverter build() {
            return new DefaultKafkaMessageConverter(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
        }
    }
}

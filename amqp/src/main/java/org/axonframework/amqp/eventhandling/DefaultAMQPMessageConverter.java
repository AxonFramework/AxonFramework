/*
 * Copyright (c) 2010-2017. Axon Framework
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

import com.rabbitmq.client.AMQP;
import org.axonframework.common.Assert;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.Headers;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.messaging.Headers.MESSAGE_TIMESTAMP;
import static org.axonframework.serialization.MessageSerializer.serializePayload;

/**
 * Default implementation of the AMQPMessageConverter interface. This implementation will suffice in most cases. It
 * passes all meta-data entries as headers (with 'axon-metadata-' prefix) to the message. Other message-specific
 * attributes are also added as meta data. The message payload is serialized using the configured serializer and passed
 * as the message body.
 *
 * @author Allard Buijze
 */
public class DefaultAMQPMessageConverter implements AMQPMessageConverter {

    private final Serializer serializer;
    private final RoutingKeyResolver routingKeyResolver;
    private final boolean durable;

    /**
     * Initializes the AMQPMessageConverter with the given {@code serializer}, using a {@link
     * PackageRoutingKeyResolver} and requesting durable dispatching.
     *
     * @param serializer The serializer to serialize the Event Message's payload with
     */
    public DefaultAMQPMessageConverter(Serializer serializer) {
        this(serializer, new PackageRoutingKeyResolver(), true);
    }

    /**
     * Initializes the AMQPMessageConverter with the given {@code serializer}, {@code routingKeyResolver} and
     * requesting durable dispatching when {@code durable} is {@code true}.
     *
     * @param serializer         The serializer to serialize the Event Message's payload and Meta Data with
     * @param routingKeyResolver The strategy to use to resolve routing keys for Event Messages
     * @param durable            Whether to request durable message dispatching
     */
    public DefaultAMQPMessageConverter(Serializer serializer, RoutingKeyResolver routingKeyResolver, boolean durable) {
        Assert.notNull(serializer, () -> "Serializer may not be null");
        Assert.notNull(routingKeyResolver, () -> "RoutingKeyResolver may not be null");
        this.serializer = serializer;
        this.routingKeyResolver = routingKeyResolver;
        this.durable = durable;
    }

    @Override
    public AMQPMessage createAMQPMessage(EventMessage<?> eventMessage) {
        SerializedObject<byte[]> serializedObject = serializePayload(eventMessage, serializer, byte[].class);
        String routingKey = routingKeyResolver.resolveRoutingKey(eventMessage);
        AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();
        Map<String, Object> headers = new HashMap<>();
        eventMessage.getMetaData().forEach((k, v) -> headers.put(Headers.MESSAGE_METADATA + "-" + k, v));
        Headers.defaultHeaders(eventMessage, serializedObject).forEach((k, v) -> {
            if (k.equals(MESSAGE_TIMESTAMP)) {
                headers.put(k, formatInstant(eventMessage.getTimestamp()));
            } else {
                headers.put(k, v);
            }
        });
        properties.headers(headers);
        if (durable) {
            properties.deliveryMode(2);
        }
        return new AMQPMessage(serializedObject.getData(), routingKey, properties.build(), false, false);
    }

    @Override
    public Optional<EventMessage<?>> readAMQPMessage(byte[] messageBody, Map<String, Object> headers) {
        if (!headers.keySet().containsAll(Arrays.asList(Headers.MESSAGE_ID, Headers.MESSAGE_TYPE))) {
            return Optional.empty();
        }
        Map<String, Object> metaData = new HashMap<>();
        headers.forEach((k, v) -> {
            if (k.startsWith(Headers.MESSAGE_METADATA + "-")) {
                metaData.put(k.substring((Headers.MESSAGE_METADATA + "-").length()), v);
            }
        });
        SimpleSerializedObject<byte[]> serializedMessage = new SimpleSerializedObject<>(messageBody, byte[].class,
                                                                                        Objects.toString(headers.get(Headers.MESSAGE_TYPE)),
                                                                                        Objects.toString(headers.get(Headers.MESSAGE_REVISION), null));
        SerializedMessage<?> message = new SerializedMessage<>(Objects.toString(headers.get(Headers.MESSAGE_ID)),
                                                               new LazyDeserializingObject<>(serializedMessage, serializer),
                                                               new LazyDeserializingObject<>(MetaData.from(metaData)));
        String timestamp = Objects.toString(headers.get(MESSAGE_TIMESTAMP));
        if (headers.containsKey(Headers.AGGREGATE_ID)) {
            return Optional.of(new GenericDomainEventMessage<>(Objects.toString(headers.get(Headers.AGGREGATE_TYPE)),
                                                   Objects.toString(headers.get(Headers.AGGREGATE_ID)),
                                                   (Long) headers.get(Headers.AGGREGATE_SEQ),
                                                   message, () -> DateTimeUtils.parseInstant(timestamp)));
        } else {
            return Optional.of(new GenericEventMessage<>(message, () -> DateTimeUtils.parseInstant(timestamp)));
        }
    }

}

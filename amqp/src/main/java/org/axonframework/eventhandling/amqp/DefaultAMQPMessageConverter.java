/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventhandling.amqp;

import com.rabbitmq.client.AMQP;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.io.EventMessageReader;
import org.axonframework.eventhandling.io.EventMessageWriter;
import org.axonframework.serialization.Serializer;

import java.io.*;
import java.util.Map;

/**
 * Default implementation of the AMQPMessageConverter interface. This implementation will suffice in most cases, unless
 * very specific requirements exist about the content of an AMQP Message's body. For example with using the Message
 * Broker to interact with non-Axon based applications.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultAMQPMessageConverter implements AMQPMessageConverter {

    private static final AMQP.BasicProperties DURABLE = new AMQP.BasicProperties.Builder().deliveryMode(2).build();

    private final Serializer serializer;
    private final RoutingKeyResolver routingKeyResolver;
    private final boolean durable;

    /**
     * Initializes the AMQPMessageConverter with the given <code>serializer</code>, using a {@link
     * PackageRoutingKeyResolver} and requesting durable dispatching.
     *
     * @param serializer The serializer to serialize the Event Message's payload and Meta Data with
     */
    public DefaultAMQPMessageConverter(Serializer serializer) {
        this(serializer, new PackageRoutingKeyResolver(), true);
    }

    /**
     * Initializes the AMQPMessageConverter with the given <code>serializer</code>, <code>routingKeyResolver</code> and
     * requesting durable dispatching when <code>durable</code> is <code>true</code>.
     *
     * @param serializer         The serializer to serialize the Event Message's payload and Meta Data with
     * @param routingKeyResolver The strategy to use to resolve routing keys for Event Messages
     * @param durable            Whether to request durable message dispatching
     */
    public DefaultAMQPMessageConverter(Serializer serializer, RoutingKeyResolver routingKeyResolver, boolean durable) {
        Assert.notNull(serializer, "Serializer may not be null");
        Assert.notNull(routingKeyResolver, "RoutingKeyResolver may not be null");
        this.serializer = serializer;
        this.routingKeyResolver = routingKeyResolver;
        this.durable = durable;
    }

    @Override
    public AMQPMessage createAMQPMessage(EventMessage eventMessage) {
        byte[] body = asByteArray(eventMessage);
        String routingKey = routingKeyResolver.resolveRoutingKey(eventMessage);
        if (durable) {
            return new AMQPMessage(body, routingKey, DURABLE, false, false);
        }
        return new AMQPMessage(body, routingKey);
    }

    @Override
    public EventMessage readAMQPMessage(byte[] messageBody, Map<String, Object> headers) {
        try {
            EventMessageReader in = new EventMessageReader(new DataInputStream(new ByteArrayInputStream(messageBody)),
                                                           serializer);
            return in.readEventMessage();
        } catch (IOException e) {
            // ByteArrayInputStream doesn't throw IOException... anyway...
            throw new EventPublicationFailedException("Failed to deserialize an EventMessage", e);
        }
    }

    private byte[] asByteArray(EventMessage event) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            EventMessageWriter outputStream = new EventMessageWriter(new DataOutputStream(baos), serializer);
            outputStream.writeEventMessage(event);
            return baos.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream doesn't throw IOException... anyway...
            throw new EventPublicationFailedException("Failed to serialize an EventMessage", e);
        }
    }
}

/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.messaging;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.NumberUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An {@link EventMessageConverter} that will convert an Axon event message into a Spring message by:
 * <ul>
 * <li>Copying axon event payload into Spring message payload</li>
 * <li>Copying axon event metadata into Spring message headers</li>
 * <li>Adding axon event message specific attributes - that are not part of axon metadata - to the Spring message Headers.
 * Among those specific attributes are {@link DomainEventMessage} specific properties</li>
 * </ul>
 *
 * @author Reda.Housni-Alaoui
 * @since 3.1
 */
public class DefaultEventMessageConverter implements EventMessageConverter {

    private static final String AXON_MESSAGE_PREFIX = "axon-message-";
    private static final String MESSAGE_ID = AXON_MESSAGE_PREFIX + "id";
    private static final String MESSAGE_TYPE = AXON_MESSAGE_PREFIX + "type";
    private static final String AGGREGATE_ID = AXON_MESSAGE_PREFIX + "aggregate-id";
    private static final String AGGREGATE_SEQ = AXON_MESSAGE_PREFIX + "aggregate-seq";
    private static final String AGGREGATE_TYPE = AXON_MESSAGE_PREFIX + "aggregate-type";

    @Override
    public <T> Message<T> convertToOutboundMessage(EventMessage<T> event) {
        Map<String, Object> headers = new HashMap<>(event.getMetaData());
        headers.put(MESSAGE_ID, event.getIdentifier());
        headers.put(MESSAGE_TYPE, event.type().toString());
        if (event instanceof DomainEventMessage) {
            headers.put(AGGREGATE_ID, ((DomainEventMessage<?>) event).getAggregateIdentifier());
            headers.put(AGGREGATE_SEQ, ((DomainEventMessage<?>) event).getSequenceNumber());
            headers.put(AGGREGATE_TYPE, ((DomainEventMessage<?>) event).getType());
        }
        return new GenericMessage<>(event.getPayload(),
                                    new SettableTimestampMessageHeaders(headers, event.getTimestamp().toEpochMilli()));
    }

    @Override
    public <T> EventMessage<T> convertFromInboundMessage(Message<T> message) {
        MessageHeaders headers = message.getHeaders();
        Map<String, ?> metaData = headers.entrySet()
                                         .stream()
                                         .filter(entry -> !entry.getKey().startsWith(AXON_MESSAGE_PREFIX))
                                         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        String messageId = Objects.toString(headers.get(MESSAGE_ID));
        MessageType type = getType(message);
        Long timestamp = headers.getTimestamp();

        org.axonframework.messaging.GenericMessage<T> genericMessage
                = new org.axonframework.messaging.GenericMessage<>(messageId, type, message.getPayload(), metaData);
        if (headers.containsKey(AGGREGATE_ID)) {
            return new GenericDomainEventMessage<>(Objects.toString(headers.get(AGGREGATE_TYPE)),
                                                   Objects.toString(headers.get(AGGREGATE_ID)),
                                                   NumberUtils.convertNumberToTargetClass(
                                                           headers.get(AGGREGATE_SEQ, Number.class), Long.class
                                                   ),
                                                   genericMessage, () -> Instant.ofEpochMilli(timestamp));
        } else {
            return new GenericEventMessage<>(genericMessage, () -> Instant.ofEpochMilli(timestamp));
        }
    }

    /**
     * If the given {@code headers} contain the {@code message type}, the {@link MessageType type} is reconstructed
     * based on the header. When it is not present, we can expect a non-Axon {@link Message} is handled. As such, we
     * base the {@code type} on the fully qualified class qualifiedName.
     */
    private static <T> MessageType getType(Message<T> message) {
        MessageHeaders headers = message.getHeaders();
        return headers.containsKey(MESSAGE_TYPE)
                ? MessageType.fromString(Objects.toString(headers.get(MESSAGE_TYPE)))
                : new MessageType(message.getClass());
    }

    private static class SettableTimestampMessageHeaders extends MessageHeaders {

        protected SettableTimestampMessageHeaders(Map<String, Object> headers, Long timestamp) {
            super(headers, null, timestamp);
        }
    }
}

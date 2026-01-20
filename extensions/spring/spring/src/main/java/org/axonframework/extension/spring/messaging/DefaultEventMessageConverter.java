/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.spring.messaging;

import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.MessageType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An {@link EventMessageConverter} that will convert an Axon event message into a Spring message by:
 * <ul>
 * <li>Copying axon event payload into Spring message payload.</li>
 * <li>Copying axon event metadata into Spring message headers.</li>
 * <li>Adding axon event message specific attributes - that are not part of axon metadata - to the Spring message Headers.</li>
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
    public <T> Message convertToOutboundMessage(EventMessage event) {
        Map<String, Object> headers = new HashMap<>(event.metadata());
        headers.put(MESSAGE_ID, event.identifier());
        headers.put(MESSAGE_TYPE, event.type().toString());
        return new GenericMessage(event.payload(),
                                  new SettableTimestampMessageHeaders(headers, event.timestamp().toEpochMilli()));
    }

    @Override
    public <T> EventMessage convertFromInboundMessage(Message message) {
        MessageHeaders headers = message.getHeaders();
        Map<String, String> metadata = headers.entrySet()
                                              .stream()
                                              .filter(entry -> !entry.getKey().startsWith(AXON_MESSAGE_PREFIX))
                                              .collect(Collectors.toMap(
                                                      Map.Entry::getKey,
                                                      entry -> entry.getValue().toString()
                                              ));

        String messageId = Objects.toString(headers.get(MESSAGE_ID));
        MessageType type = getType(message);
        Long timestamp = headers.getTimestamp();

        org.axonframework.messaging.core.GenericMessage genericMessage
                = new org.axonframework.messaging.core.GenericMessage(messageId, type, message.getPayload(), metadata);
        //noinspection DataFlowIssue - Just let it throw a NullPointerException if the timestamp is null
        return new GenericEventMessage(genericMessage, () -> Instant.ofEpochMilli(timestamp));
    }

    /**
     * If the given {@code headers} contain the {@code message type}, the {@link MessageType type} is reconstructed
     * based on the header. When it is not present, we can expect a non-Axon {@link Message} is handled. As such, we
     * base the {@code type} on the fully qualified class qualifiedName.
     */
    private static <T> MessageType getType(Message message) {
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

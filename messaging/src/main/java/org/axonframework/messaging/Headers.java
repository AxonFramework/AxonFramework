/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.messaging;

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.serialization.SerializedObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Default headers to include when publishing a message on a broker.
 *
 * @author Nakul Mishra
 * @since 3.0
 */
public abstract class Headers {

    /**
     * Key pointing to a message identifier.
     */
    public static final String MESSAGE_ID = "axon-message-id";
    /**
     * Key pointing to the serialized payload of a message.
     */
    public static final String SERIALIZED_MESSAGE_PAYLOAD = "axon-serialized-message-payload";
    /**
     * Key pointing to the payload type of a message.
     */
    public static final String MESSAGE_TYPE = "axon-message-type";
    /**
     * Key pointing to the revision of a message.
     */
    public static final String MESSAGE_REVISION = "axon-message-revision";
    /**
     * Key pointing to the timestamp of a message.
     */
    public static final String MESSAGE_TIMESTAMP = "axon-message-timestamp";
    /**
     * Key pointing to the aggregate identifier of a message.
     */
    public static final String AGGREGATE_ID = "axon-message-aggregate-id";
    /**
     * Key pointing to the aggregate sequence of a message.
     */
    public static final String AGGREGATE_SEQ = "axon-message-aggregate-seq";
    /**
     * Key pointing to the aggregate type of a message.
     */
    public static final String AGGREGATE_TYPE = "axon-message-aggregate-type";
    /**
     * Key pointing to the {@link MetaData} of a message.
     */
    public static final String MESSAGE_METADATA = "axon-metadata";
    /**
     * Key pointing to the deadline name of a {@link org.axonframework.deadline.DeadlineMessage}.
     */
    public static final String DEADLINE_NAME = "axon-deadline-name";

    private Headers() {
    }

    /**
     * Generate defaults headers to recognise an event message.
     *
     * @param message          event message.
     * @param serializedObject payload.
     * @return headers
     */
    public static Map<String, Object> defaultHeaders(EventMessage<?> message,
                                                     SerializedObject<?> serializedObject) {
        Assert.notNull(message, () -> "Event message cannot be null");
        Assert.notNull(serializedObject, () -> "Serialized Object cannot be null");
        Assert.notNull(serializedObject.getType(), () -> "SerializedObject Type cannot be null");
        HashMap<String, Object> headers = new HashMap<>();
        headers.put(MESSAGE_ID, message.getIdentifier());
        headers.put(MESSAGE_TYPE, serializedObject.getType().getName());
        headers.put(MESSAGE_REVISION, serializedObject.getType().getRevision());
        headers.put(MESSAGE_TIMESTAMP, message.getTimestamp());

        if (message instanceof DomainEventMessage) {
            headers.put(AGGREGATE_ID, ((DomainEventMessage<?>) message).getAggregateIdentifier());
            headers.put(AGGREGATE_SEQ, ((DomainEventMessage<?>) message).getSequenceNumber());
            headers.put(AGGREGATE_TYPE, ((DomainEventMessage<?>) message).getType());
        }

        return Collections.unmodifiableMap(headers);
    }

    @Override
    public String toString() {
        return "[Headers]";
    }
}

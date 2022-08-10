/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.deadletter.jpa;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SimpleSerializedObject;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Lob;

import static java.util.Objects.requireNonNull;

/**
 * Represents an {@link org.axonframework.eventhandling.EventMessage} when stored into the database.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
@Embeddable
public class DeadLetterEventEntry {

    @Basic(optional = false)
    private String messageType;

    @Column(nullable = false)
    private String eventIdentifier;

    @Basic(optional = false)
    private String timeStamp;

    @Basic(optional = false)
    private String payloadType;

    @Basic
    private String payloadRevision;

    @Basic(optional = false)
    @Lob
    @Column(length = 10000)
    private byte[] payload;

    @Basic
    @Lob
    @Column(length = 10000)
    private byte[] metaData;

    @Basic
    private String type;

    @Basic
    private String aggregateIdentifier;

    @Basic
    private Long sequenceNumber;

    @Basic
    private String tokenType;

    @Basic
    @Lob
    @Column(length = 10000)
    private byte[] token;

    protected DeadLetterEventEntry() {
        // required by JPA
    }

    /**
     * Constructs a new {@link DeadLetterEventEntry} using the provided parameters.
     */
    public DeadLetterEventEntry(String messageType, String eventIdentifier, String timeStamp, String payloadType,
                                String payloadRevision, byte[] payload, byte[] metaData, String type,
                                String aggregateIdentifier, Long sequenceNumber, String tokenType, byte[] token) {
        requireNonNull(messageType,
                       "Message type should be provided by the DeadLetterJpaConverter, otherwise it can never be converted back.");
        this.messageType = messageType;
        this.eventIdentifier = eventIdentifier;
        this.timeStamp = timeStamp;
        this.payloadType = payloadType;
        this.payloadRevision = payloadRevision;
        this.payload = payload;
        this.metaData = metaData;
        this.type = type;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.tokenType = tokenType;
        this.token = token;
    }

    /**
     * Returns the message type, which is defined by the {@link DeadLetterJpaConverter} which mapped this entry. Used
     * for later matching whether a converter can convert it back to an
     * {@link org.axonframework.eventhandling.EventMessage}.
     *
     * @return The message type.
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * Returns the original {@link EventMessage#getIdentifier()}.
     *
     * @return The event identifier.
     */
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    /**
     * Returns the original {@link EventMessage#getTimestamp()}.
     *
     * @return The event timestamp.
     */
    public String getTimeStamp() {
        return timeStamp;
    }

    /**
     * Returns the original payload as a {@link SimpleSerializedObject}.
     *
     * @return The original payload.
     */
    public SimpleSerializedObject<byte[]> getPayload() {
        return new SimpleSerializedObject<>(
                payload,
                byte[].class,
                payloadType,
                payloadRevision);
    }

    /**
     * Returns the original metadata as a {@link SimpleSerializedObject}.
     *
     * @return The original metadata.
     */
    public SimpleSerializedObject<byte[]> getMetaData() {
        return new SimpleSerializedObject<>(
                metaData,
                byte[].class,
                MetaData.class.getName(),
                null);
    }

    /**
     * Returns the original {@link DomainEventMessage#getType()}, if it was a {@code DomainEventMessage}.
     *
     * @return The original aggregate type.
     */
    public String getType() {
        return type;
    }


    /**
     * Returns the original {@link DomainEventMessage#getAggregateIdentifier()}, if it was a
     * {@code DomainEventMessage}.
     *
     * @return The original aggregate identifier.
     */
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }


    /**
     * Returns the original {@link DomainEventMessage#getSequenceNumber()}, if it was a {@code DomainEventMessage}.
     *
     * @return The original aggregate sequence number.
     */
    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the original {@link TrackedEventMessage#trackingToken()} as a
     * {@link org.axonframework.serialization.SerializedObject}, if the original message was a
     * {@code TrackedEventMessage}.
     *
     * @return The original tracking token.
     */
    public SimpleSerializedObject<byte[]> getTrackingToken() {
        if (token == null) {
            return null;
        }
        return new SimpleSerializedObject<>(
                token,
                byte[].class,
                tokenType,
                null);
    }
}

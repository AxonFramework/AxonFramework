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

package org.axonframework.eventhandling.deadletter.legacyjpa;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.deadletter.jpa.DeadLetterJpaConverter;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SimpleSerializedObject;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Lob;
import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents an {@link EventMessage} when stored into the database. It contains all properties known in the framework
 * implementations. Based on which properties are present, the original message type can be determined. For example, if
 * an aggregate identifier is present, it was a {@link DomainEventMessage}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 * @deprecated in favor of using {@link org.axonframework.eventhandling.deadletter.jpa.DeadLetterEventEntry} which moved
 * to jakarta.
 */
@Deprecated
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
     * Constructs a new {@link DeadLetterEventEntry} using the provided parameters. Parameters can be null if it's not
     * relevant for the {@code messageType}. For example, a {@link DomainEventMessage} contains an
     * {@code aggregateType}, {@code aggregateIdentifier} and {@code sequenceNumber}, but a
     * {@link org.axonframework.eventhandling.GenericEventMessage} does not.
     *
     * @param messageType         The message type (required).
     * @param messageIdentifier   The identifier of the message (required).
     * @param messageTimestamp    The timestamp of the message (required).
     * @param payloadType         The payload's type of the message.
     * @param payloadRevision     The payload's revision of the message.
     * @param payload             The serialized payload of the message.
     * @param metaData            The serialized metadata of the message.
     * @param aggregateType       The aggregate type of the message.
     * @param aggregateIdentifier The aggregate identifier of the message.
     * @param sequenceNumber      The aggregate sequence number of the message.
     * @param tokenType           The type of tracking token the message.
     * @param token               The serialized tracking token.
     */
    public DeadLetterEventEntry(String messageType, String messageIdentifier, String messageTimestamp,
                                String payloadType,
                                String payloadRevision, byte[] payload, byte[] metaData, String aggregateType,
                                String aggregateIdentifier, Long sequenceNumber, String tokenType, byte[] token) {
        requireNonNull(messageType,
                       "Message type should be provided by the DeadLetterJpaConverter, otherwise it can never be converted back.");
        requireNonNull(messageIdentifier, "All EventMessage implementations require a message identifier.");
        requireNonNull(messageTimestamp, "All EventMessage implementations require a timestamp.");
        this.messageType = messageType;
        this.eventIdentifier = messageIdentifier;
        this.timeStamp = messageTimestamp;
        this.payloadType = payloadType;
        this.payloadRevision = payloadRevision;
        this.payload = payload;
        this.metaData = metaData;
        this.type = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.tokenType = tokenType;
        this.token = token;
    }

    /**
     * Returns the message type, which is defined by the {@link DeadLetterJpaConverter} that mapped this entry. Used for
     * later matching whether a converter can convert it back to an {@link EventMessage}.
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeadLetterEventEntry that = (DeadLetterEventEntry) o;

        if (!messageType.equals(that.messageType)) {
            return false;
        }
        if (!Objects.equals(eventIdentifier, that.eventIdentifier)) {
            return false;
        }
        if (!Objects.equals(timeStamp, that.timeStamp)) {
            return false;
        }
        if (!Objects.equals(payloadType, that.payloadType)) {
            return false;
        }
        if (!Objects.equals(payloadRevision, that.payloadRevision)) {
            return false;
        }
        if (!Arrays.equals(payload, that.payload)) {
            return false;
        }
        if (!Arrays.equals(metaData, that.metaData)) {
            return false;
        }
        if (!Objects.equals(type, that.type)) {
            return false;
        }
        if (!Objects.equals(aggregateIdentifier, that.aggregateIdentifier)) {
            return false;
        }
        if (!Objects.equals(sequenceNumber, that.sequenceNumber)) {
            return false;
        }
        if (!Objects.equals(tokenType, that.tokenType)) {
            return false;
        }
        return Arrays.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        int result = messageType.hashCode();
        result = 31 * result + (eventIdentifier != null ? eventIdentifier.hashCode() : 0);
        result = 31 * result + (timeStamp != null ? timeStamp.hashCode() : 0);
        result = 31 * result + (payloadType != null ? payloadType.hashCode() : 0);
        result = 31 * result + (payloadRevision != null ? payloadRevision.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(payload);
        result = 31 * result + Arrays.hashCode(metaData);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (aggregateIdentifier != null ? aggregateIdentifier.hashCode() : 0);
        result = 31 * result + (sequenceNumber != null ? sequenceNumber.hashCode() : 0);
        result = 31 * result + (tokenType != null ? tokenType.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(token);
        return result;
    }

    @Override
    public String toString() {
        return "DeadLetterEventEntry{" +
                "messageType='" + messageType + '\'' +
                ", eventIdentifier='" + eventIdentifier + '\'' +
                ", timeStamp='" + timeStamp + '\'' +
                ", payloadType='" + payloadType + '\'' +
                ", payloadRevision='" + payloadRevision + '\'' +
                ", payload=" + Arrays.toString(payload) +
                ", metaData=" + Arrays.toString(metaData) +
                ", type='" + type + '\'' +
                ", aggregateIdentifier='" + aggregateIdentifier + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", tokenType='" + tokenType + '\'' +
                ", token=" + Arrays.toString(token) +
                '}';
    }
}

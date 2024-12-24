/*
 * Copyright (c) 2010-2024. Axon Framework
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

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.serialization.SimpleSerializedObject;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents an {@link org.axonframework.eventhandling.EventMessage} when stored into the database. It contains all
 * properties known in the framework implementations. Based on which properties are present, the original message type
 * can be determined. For example, if an aggregate identifier is present, it was a {@link DomainEventMessage}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
@Embeddable
public class DeadLetterEventEntry {

    @Basic(optional = false)
    private String eventType;

    @Column(nullable = false)
    private String eventIdentifier;

    @Column(nullable = false)
    private String name;

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
    private String aggregateType;

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
     * relevant for the {@code eventType}. For example, a {@link DomainEventMessage} contains an {@code aggregateType},
     * {@code aggregateIdentifier} and {@code sequenceNumber}, but a
     * {@link org.axonframework.eventhandling.GenericEventMessage} does not.
     *
     * @param eventType           The event type (required).
     * @param eventIdentifier     The identifier of the message (required).
     * @param name                The {@link EventMessage#name()} as a {@code String}, based on the output of
     *                            {@link QualifiedName#toString()}.
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
    public DeadLetterEventEntry(String eventType,
                                String eventIdentifier,
                                String name,
                                String messageTimestamp,
                                String payloadType,
                                String payloadRevision,
                                byte[] payload,
                                byte[] metaData,
                                String aggregateType,
                                String aggregateIdentifier,
                                Long sequenceNumber,
                                String tokenType,
                                byte[] token) {
        requireNonNull(eventType,
                       "Event type should be provided by the DeadLetterJpaConverter, otherwise it can never be converted back.");
        requireNonNull(eventIdentifier, "All EventMessage implementations require a message identifier.");
        requireNonNull(name, "All EventMessage implementations require a name.");
        requireNonNull(messageTimestamp, "All EventMessage implementations require a timestamp.");
        this.eventType = eventType;
        this.eventIdentifier = eventIdentifier;
        this.name = name;
        this.timeStamp = messageTimestamp;
        this.payloadType = payloadType;
        this.payloadRevision = payloadRevision;
        this.payload = payload;
        this.metaData = metaData;
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.tokenType = tokenType;
        this.token = token;
    }

    /**
     * Returns the event type, which is defined by the {@link DeadLetterJpaConverter} that mapped this entry.
     * <p>
     * Used for later matching whether a converter can convert it back to an
     * {@link org.axonframework.eventhandling.EventMessage}.
     *
     * @return The event type.
     */
    public String getEventType() {
        return eventType;
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
     * Returns the original {@link EventMessage#name() name} of the dead-letter, based on the
     * {@link QualifiedName#toString()} output.
     *
     * @return The original {@link EventMessage#name() name} of the dead-letter, based on the
     * {@link QualifiedName#toString()} output.
     */
    public String getName() {
        return name;
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
        return new SimpleSerializedObject<>(payload, byte[].class, payloadType, payloadRevision);
    }

    /**
     * Returns the original metadata as a {@link SimpleSerializedObject}.
     *
     * @return The original metadata.
     */
    public SimpleSerializedObject<byte[]> getMetaData() {
        return new SimpleSerializedObject<>(metaData, byte[].class, MetaData.class.getName(), null);
    }

    /**
     * Returns the original {@link DomainEventMessage#getType()}, if it was a {@code DomainEventMessage}.
     *
     * @return The original aggregate type.
     */
    public String getAggregateType() {
        return aggregateType;
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
        return new SimpleSerializedObject<>(token, byte[].class, tokenType, null);
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
        return Objects.equals(eventType, that.eventType)
                && Objects.equals(eventIdentifier, that.eventIdentifier)
                && Objects.equals(name, that.name)
                && Objects.equals(timeStamp, that.timeStamp)
                && Objects.equals(payloadType, that.payloadType)
                && Objects.equals(payloadRevision, that.payloadRevision)
                && Objects.deepEquals(payload, that.payload)
                && Objects.deepEquals(metaData, that.metaData)
                && Objects.equals(aggregateType, that.aggregateType)
                && Objects.equals(aggregateIdentifier, that.aggregateIdentifier)
                && Objects.equals(sequenceNumber, that.sequenceNumber)
                && Objects.equals(tokenType, that.tokenType)
                && Objects.deepEquals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType,
                            eventIdentifier,
                            name,
                            timeStamp,
                            payloadType,
                            payloadRevision,
                            Arrays.hashCode(payload),
                            Arrays.hashCode(metaData),
                            aggregateType,
                            aggregateIdentifier,
                            sequenceNumber,
                            tokenType,
                            Arrays.hashCode(token));
    }

    @Override
    public String toString() {
        return "DeadLetterEventEntry{" +
                "eventType='" + eventType + '\'' +
                ", eventIdentifier='" + eventIdentifier + '\'' +
                ", name='" + name + '\'' +
                ", timeStamp='" + timeStamp + '\'' +
                ", payloadType='" + payloadType + '\'' +
                ", payloadRevision='" + payloadRevision + '\'' +
                ", payload=" + Arrays.toString(payload) +
                ", metaData=" + Arrays.toString(metaData) +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateIdentifier='" + aggregateIdentifier + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", tokenType='" + tokenType + '\'' +
                ", token=" + Arrays.toString(token) +
                '}';
    }
}

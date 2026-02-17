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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents an {@link EventMessage} when stored into the database. It contains all properties known in the framework
 * implementations. Tracking tokens and aggregate data (only if legacy Aggregate approach is used: aggregate identifier, type, sequence
 * number) are stored as {@link Context} resources.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
@Embeddable
public class DeadLetterEventEntry {

    @Basic(optional = false)
    private String type;

    @Column(nullable = false)
    private String eventIdentifier; // TODO #3517 - rename to just "identifier"? Everything is related to event here

    @Basic(optional = false)
    private String timeStamp; // TODO #3517 - change it to "timestamp"?

    @Basic(optional = false)
    @Lob
    @Column(length = 10000)
    private byte[] payload;

    @Basic
    @Lob
    @Column(length = 10000)
    private byte[] metadata;

    @Basic
    private String aggregateType;

    @Basic
    private String aggregateIdentifier;

    @Basic
    private Long sequenceNumber; // TODO #3517 - rename to "aggregateSequenceNumber"?

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
     * Constructs a new {@link DeadLetterEventEntry} using the provided parameters. Parameters can be null if not
     * relevant. For example, aggregate info (type, identifier, sequence number) is stored when the event was published
     * in an aggregate context (stored as context resources), and tracking token is stored when processing from an event
     * stream.
     *
     * @param type                The {@link MessageType} of the event as a {@code String}, based on the output of
     *                            {@link MessageType#toString()} (required).
     * @param eventIdentifier     The identifier of the message (required).
     * @param messageTimestamp    The timestamp of the message (required).
     * @param payload             The serialized payload of the message.
     * @param metadata            The serialized metadata of the message.
     * @param aggregateType       The aggregate type of the message.
     * @param aggregateIdentifier The aggregate identifier of the message.
     * @param sequenceNumber      The aggregate sequence number of the message.
     * @param tokenType           The type of tracking token the message.
     * @param token               The serialized tracking token.
     */
    public DeadLetterEventEntry(String type,
                                String eventIdentifier,
                                String messageTimestamp,
                                byte[] payload,
                                byte[] metadata,
                                String aggregateType,
                                String aggregateIdentifier,
                                Long sequenceNumber,
                                String tokenType,
                                byte[] token) {
        requireNonNull(type,
                       "Event type should be provided by the DeadLetterJpaConverter, otherwise it can never be converted back.");
        requireNonNull(eventIdentifier, "All EventMessage implementations require a message identifier.");
        requireNonNull(messageTimestamp, "All EventMessage implementations require a timestamp.");
        this.type = type;
        this.eventIdentifier = eventIdentifier;
        this.timeStamp = messageTimestamp;
        this.payload = payload;
        this.metadata = metadata;
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
        this.sequenceNumber = sequenceNumber;
        this.tokenType = tokenType;
        this.token = token;
    }

    /**
     * Returns the original {@link Message#type() type} of the dead-letter, based on the {@link MessageType#toString()}
     * output.
     *
     * @return The event message type.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the original {@link EventMessage#identifier()}.
     *
     * @return The event identifier.
     */
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    /**
     * Returns the original {@link EventMessage#timestamp()}.
     *
     * @return The event timestamp.
     */
    public String getTimeStamp() {
        return timeStamp;
    }

    /**
     * Returns the serialized payload as a byte array.
     *
     * @return The serialized payload bytes.
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * Returns the serialized metadata as a byte array.
     *
     * @return The serialized metadata bytes.
     */
    public byte[] getMetadata() {
        return metadata;
    }

    /**
     * Returns the aggregate type, if the event was published in an aggregate context (stored as context resource).
     *
     * @return The aggregate type, or {@code null} if not available.
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * Returns the aggregate identifier, if the event was published in an aggregate context (stored as context
     * resource).
     *
     * @return The aggregate identifier, or {@code null} if not available.
     */
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Returns the aggregate sequence number, if the event was published in an aggregate context (stored as context
     * resource).
     *
     * @return The aggregate sequence number, or {@code null} if not available.
     */
    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Returns the serialized tracking token as a byte array, if the event was being processed from an event stream.
     *
     * @return The serialized tracking token bytes, or {@code null} if not available.
     */
    public byte[] getToken() {
        return token;
    }

    /**
     * Returns the tracking token type name.
     *
     * @return The fully qualified class name of the tracking token type, or {@code null} if no token was stored.
     */
    public String getTokenType() {
        return tokenType;
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
        return Objects.equals(type, that.type)
                && Objects.equals(eventIdentifier, that.eventIdentifier)
                && Objects.equals(timeStamp, that.timeStamp)
                && Objects.deepEquals(payload, that.payload)
                && Objects.deepEquals(metadata, that.metadata)
                && Objects.equals(aggregateType, that.aggregateType)
                && Objects.equals(aggregateIdentifier, that.aggregateIdentifier)
                && Objects.equals(sequenceNumber, that.sequenceNumber)
                && Objects.equals(tokenType, that.tokenType)
                && Objects.deepEquals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type,
                            eventIdentifier,
                            timeStamp,
                            Arrays.hashCode(payload),
                            Arrays.hashCode(metadata),
                            aggregateType,
                            aggregateIdentifier,
                            sequenceNumber,
                            tokenType,
                            Arrays.hashCode(token));
    }

    @Override
    public String toString() {
        return "DeadLetterEventEntry{" +
                "type='" + type + '\'' +
                ", eventIdentifier='" + eventIdentifier + '\'' +
                ", timeStamp='" + timeStamp + '\'' +
                ", payload=" + Arrays.toString(payload) +
                ", metadata=" + Arrays.toString(metadata) +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateIdentifier='" + aggregateIdentifier + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", tokenType='" + tokenType + '\'' +
                ", token=" + Arrays.toString(token) +
                '}';
    }
}

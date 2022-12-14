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

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Default DeadLetter JPA entity implementation of dead letters. Used by the {@link JpaSequencedDeadLetterQueue} to
 * store these into the database to be retried later.
 * <p>
 * The original message is embedded as a {@link DeadLetterEventEntry}. This is mapped, upon both storage and retrieval,
 * by one of the configured {@link DeadLetterJpaConverter converters}.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
@Entity
@Table(indexes = {
        @Index(columnList = "processingGroup"),
        @Index(columnList = "processingGroup,sequenceIdentifier"),
        @Index(columnList = "processingGroup,sequenceIdentifier,sequenceIndex", unique = true),
})
public class DeadLetterEntry {

    @Id
    private String deadLetterId;

    @Basic(optional = false)
    private String processingGroup;

    @Basic(optional = false)
    private String sequenceIdentifier;

    @Basic(optional = false)
    private long sequenceIndex;

    @Embedded
    private DeadLetterEventEntry message;

    @Basic(optional = false)
    private Instant enqueuedAt;

    private Instant lastTouched;

    private Instant processingStarted;

    private String causeType;
    private String causeMessage;

    @Basic
    @Lob
    @Column(length = 10000)
    private byte[] diagnostics;


    /**
     * Constructor required by JPA. Do not use.
     */
    protected DeadLetterEntry() {
        // required by JPA
    }

    /**
     * Creates a new {@link DeadLetterEntry} consisting of the given parameters.
     *
     * @param processingGroup    The processing group this message belongs to.
     * @param sequenceIdentifier The sequence identifier this message belongs to.
     * @param sequenceIndex              The index of this message within the sequence.
     * @param message            An embedded {@link DeadLetterEventEntry} containing all information about the message
     *                           itself.
     * @param enqueuedAt         The time the message was enqueued.
     * @param lastTouched        The time the message has been last processed.
     * @param cause              The reason the message was enqueued.
     * @param diagnostics        The diagnostics, a map of metadata.
     * @param serializer         The {@link Serializer } to use for the {@code diagnostics}
     */
    public DeadLetterEntry(String processingGroup,
                           String sequenceIdentifier,
                           long sequenceIndex,
                           DeadLetterEventEntry message,
                           Instant enqueuedAt,
                           Instant lastTouched,
                           Cause cause,
                           MetaData diagnostics,
                           Serializer serializer) {
        this.deadLetterId = IdentifierFactory.getInstance().generateIdentifier();
        this.processingGroup = processingGroup;
        this.sequenceIdentifier = sequenceIdentifier;
        this.sequenceIndex = sequenceIndex;
        this.message = message;
        this.enqueuedAt = enqueuedAt;
        this.lastTouched = lastTouched;
        if (cause != null) {
            this.causeType = cause.type();
            this.causeMessage = cause.message();
        }
        this.setDiagnostics(diagnostics, serializer);
    }

    /**
     * The unique ID of this dead letter in the database.
     *
     * @return The unique ID.
     */
    public String getDeadLetterId() {
        return deadLetterId;
    }

    /**
     * The processing group this dead letter belongs to.
     *
     * @return The processing group.
     */
    public String getProcessingGroup() {
        return processingGroup;
    }

    /**
     * The sequence identifier of this dead letter. If two have the same, they must be handled sequentially.
     *
     * @return The sequence identifier.
     */
    public String getSequenceIdentifier() {
        return sequenceIdentifier;
    }

    /**
     * The index of this message within the {@link #getSequenceIdentifier()}, used for keeping the messages in the same
     * order within the same sequence.
     *
     * @return The index.
     */
    public long getSequenceIndex() {
        return sequenceIndex;
    }

    /**
     * The embedded {@link DeadLetterEventEntry} representing the original message.
     *
     * @return The embedded original message.
     */
    public DeadLetterEventEntry getMessage() {
        return message;
    }

    /**
     * The time the message was enqueued.
     *
     * @return The time the message was enqueued.
     */
    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    /**
     * The time the messages was last touched, meaning having been queued or having been tried to process.
     *
     * @return The time the messages was last touched.
     */
    public Instant getLastTouched() {
        return lastTouched;
    }

    /**
     * Sets the time the message was last touched. Should be updated every time the message has been attempted to
     * process.
     *
     * @param lastTouched The new time to set to.
     */
    public void setLastTouched(Instant lastTouched) {
        this.lastTouched = lastTouched;
    }

    /**
     * Timestamp indicating when the processing of this dead letter has started. Used for claiminig messages and
     * preventing multiple processes or thread from handling items concurrently within the same sequence.
     *
     * @return Timestamp of start processing.
     */
    public Instant getProcessingStarted() {
        return processingStarted;
    }

    /**
     * Gets the class of the original exception.
     *
     * @return The type of the cause.
     */
    public String getCauseType() {
        return causeType;
    }

    /**
     * Gets the message of the original exception.
     *
     * @return The message of the cause.
     */
    public String getCauseMessage() {
        return causeMessage;
    }

    /**
     * Returns the serialized diagnostics.
     *
     * @return The serialized diagnostics.
     */
    public SerializedObject<byte[]> getDiagnostics() {
        return new SimpleSerializedObject<>(
                diagnostics,
                byte[].class,
                MetaData.class.getName(),
                null);
    }

    /**
     * Sets the cause of the error when the message was originally processed, or processed later and the cause was
     * updated.
     *
     * @param cause The new cause to set to.
     */
    public void setCause(Cause cause) {
        this.causeType = cause != null ? cause.type() : null;
        this.causeMessage = cause != null ? cause.message() : null;
    }

    /**
     * Sets the diagnostics, taking in a {@link Serializer} to serialize if to the correct format.
     *
     * @param diagnostics The new diagnostics.
     * @param serializer  The {@link Serializer} to use.
     */
    public void setDiagnostics(MetaData diagnostics, Serializer serializer) {
        SerializedObject<byte[]> serializedDiagnostics = serializer.serialize(diagnostics, byte[].class);
        this.diagnostics = serializedDiagnostics.getData();
    }

    /**
     * Releases the message for processing by another thread or process.
     */
    public void clearProcessingStarted() {
        this.processingStarted = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeadLetterEntry that = (DeadLetterEntry) o;

        return Objects.equals(deadLetterId, that.deadLetterId);
    }

    @Override
    public int hashCode() {
        return deadLetterId != null ? deadLetterId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "DeadLetterEntry{" +
                "deadLetterId='" + deadLetterId + '\'' +
                ", processingGroup='" + processingGroup + '\'' +
                ", sequenceIdentifier='" + sequenceIdentifier + '\'' +
                ", index=" + sequenceIndex +
                ", message=" + message +
                ", enqueuedAt=" + enqueuedAt +
                ", lastTouched=" + lastTouched +
                ", processingStarted=" + processingStarted +
                ", causeType='" + causeType + '\'' +
                ", causeMessage='" + causeMessage + '\'' +
                ", diagnostics=" + Arrays.toString(diagnostics) +
                '}';
    }
}

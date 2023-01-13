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

package org.axonframework.eventhandling.tokenstore.jpa;

import jakarta.persistence.*;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.serialization.*;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Objects;

import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.eventhandling.tokenstore.AbstractTokenEntry.clock;

/**
 * Implementation of a token entry compatible with JPA that stores its serialized token as a byte array.
 *
 * @author Rene de Waele
 * @author Allard Buijze
 */
@Entity
@IdClass(TokenEntry.PK.class)
public class TokenEntry {

    @Lob
    @Column(length = 10000)
    private byte[] token;
    @Basic
    private String tokenType;

    @Basic(optional = false)
    private String timestamp;

    @Basic
    private String owner;

    @Id
    private String processorName;
    @Id
    private int segment;

    /**
     * Initializes a new token entry for given {@code token}, {@code processorName} and {@code segment}. The given
     * {@code serializer} can be used to serialize the token before it is stored.
     *
     * @param token         The tracking token to store
     * @param processorName The name of the processor to store this token for
     * @param segment       The segment index of the processor
     * @param serializer    The serializer to use when storing a serialized token
     */
    public TokenEntry(String processorName, int segment, TrackingToken token, Serializer serializer) {
        this.timestamp = formatInstant(clock.instant());
        if (token != null) {
            SerializedObject<byte[]> serializedToken = serializer.serialize(token, byte[].class);
            this.token = serializedToken.getData();
            tokenType = serializedToken.getType().getName();
        }
        this.processorName = processorName;
        this.segment = segment;
    }

    /**
     * Default constructor for JPA
     */
    @SuppressWarnings("unused")
    protected TokenEntry() {
    }

    /**
     * Returns the serialized token.
     *
     * @return the serialized token stored in this entry
     */
    public SerializedObject<byte[]> getSerializedToken() {
        if (token == null) {
            return null;
        }
        return new SimpleSerializedObject<>(token, byte[].class, getTokenType());
    }

    /**
     * Returns the token, deserializing it with given {@code serializer}
     *
     * @param serializer The serialize to deserialize the token with
     * @return the deserialized token stored in this entry
     */
    public TrackingToken getToken(Serializer serializer) {
        return token == null ? null : serializer.deserialize(getSerializedToken());
    }

    /**
     * Returns the {@link SerializedType} of the serialized token, or {@code null} if no token is stored by this entry.
     *
     * @return the serialized type of the token, or {@code null} if no token is stored by this entry
     */
    public SerializedType getTokenType() {
        return tokenType != null ? new SimpleSerializedType(tokenType, null) : null;
    }


    /**
     * Attempt to claim ownership of this token. When successful, this method returns {@code true}, otherwise
     * {@code false}. When a claim fails, this token should not be used, as it is already being used in another
     * process.
     * <p>
     * If a claim exists, but it is older than given {@code claimTimeout}, the claim may be 'stolen'.
     *
     * @param owner        The name of the current node, to register as owner. This name must be unique for multiple
     *                     instances of the same logical processor
     * @param claimTimeout The time after which a claim may be 'stolen' from its current owner
     * @return {@code true} if the claim succeeded, otherwise false
     */
    public boolean claim(String owner, TemporalAmount claimTimeout) {
        if (!mayClaim(owner, claimTimeout)) {
            return false;
        }
        this.timestamp = formatInstant(clock.instant());
        this.owner = owner;
        return true;
    }

    /**
     * Check if given {@code owner} may claim this token.
     *
     * @param owner        The name of the current node, to register as owner. This name must be unique for multiple
     *                     instances of the same logical processor
     * @param claimTimeout The time after which a claim may be 'stolen' from its current owner
     * @return {@code true} if the claim may be made, {@code false} otherwise
     */
    public boolean mayClaim(String owner, TemporalAmount claimTimeout) {
        return this.owner == null || owner.equals(this.owner) || expired(claimTimeout);
    }

    private boolean expired(TemporalAmount claimTimeout) {
        return timestamp().plus(claimTimeout).isBefore(clock.instant());
    }

    /**
     * Release any claim of ownership currently active on this Token, if owned by the given {@code owner}.
     *
     * @param owner The name of the current node, which was registered as the owner
     * @return {@code true} of the claim was successfully released, or {@code false} if the token has been claimed by
     * another owner.
     */
    public boolean releaseClaim(String owner) {
        if (Objects.equals(this.owner, owner)) {
            this.owner = null;
            this.timestamp = formatInstant(clock.instant());
        }
        return this.owner == null;
    }

    /**
     * Returns the storage timestamp of this token entry as a String.
     *
     * @return The storage timestamp as string
     */
    public String timestampAsString() {
        return timestamp;
    }

    /**
     * Returns the storage timestamp of this token entry.
     *
     * @return The storage timestamp
     */
    public Instant timestamp() {
        return DateTimeUtils.parseInstant(timestamp);
    }

    /**
     * Updates a token, using the provided token and serializer to update the serialized token and token type.
     * It will also update the timestamp to the current time, using the inhirited static {@link java.time.Clock}.
     *
     * @param token      The new token that needs to be persisted
     * @param serializer The serializer that will be used to serialize the token
     */
    public void updateToken(TrackingToken token, Serializer serializer) {
        SerializedObject<byte[]> serializedToken = serializer.serialize(token, byte[].class);
        this.token = serializedToken.getData();
        this.tokenType = serializedToken.getType().getName();
        this.timestamp = formatInstant(clock.instant());
    }

    /**
     * Returns the identifier of the process (JVM) having a claim on this token, or {@code null} if the token isn't
     * claimed.
     *
     * @return the process (JVM) that claimed this token
     */
    public String getOwner() {
        return owner;
    }

    public String getProcessorName() {
        return processorName;
    }

    public int getSegment() {
        return segment;
    }

    /**
     * Primary key for token entries used by JPA
     */
    @SuppressWarnings("UnusedDeclaration")
    public static class PK implements Serializable {
        private static final long serialVersionUID = 1L;

        private String processorName;
        private int segment;

        /**
         * Constructor for JPA
         */
        public PK() {
        }

        /**
         * Constructs a primary key for a TokenEntry
         *
         * @param processorName The name of the processor
         * @param segment       the index of the processing segment
         */
        public PK(String processorName, int segment) {
            this.processorName = processorName;
            this.segment = segment;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PK pk = (PK) o;
            return segment == pk.segment && Objects.equals(processorName, pk.processorName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(processorName, segment);
        }
    }


}

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

package org.axonframework.eventhandling.tokenstore;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import jakarta.persistence.MappedSuperclass;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Objects;

import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * Abstract base class of a JPA entry containing a serialized tracking token belonging to a given process.
 * For use with Jakarta/Hibernate 6 use {@link org.axonframework.eventhandling.tokenstore.jpa.TokenEntry} instead.
 *
 * @param <T> The serialized data type of the token
 * @author Rene de Waele
 */
@MappedSuperclass
public abstract class AbstractTokenEntry<T> {

    /**
     * The clock used to persist timestamps in this entry. Defaults to UTC system time.
     */
    public static Clock clock = Clock.systemUTC();

    @Lob
    @Column(length = 10000)
    private T token;
    @Basic
    private String tokenType;

    @Basic(optional = false)
    private String timestamp;

    @Basic
    private String owner;

    /**
     * Initializes a new token entry for given {@code token}, {@code process} and {@code segment}. The given {@code
     * serializer} can be used to serialize the token before it is stored.
     *
     * @param token       The tracking token to store
     * @param serializer  The serializer to use when storing a serialized token
     * @param contentType The content type after serialization
     */
    protected AbstractTokenEntry(TrackingToken token, Serializer serializer, Class<T> contentType) {
        this.timestamp = formatInstant(clock.instant());
        if (token != null) {
            SerializedObject<T> serializedToken = serializer.serialize(token, contentType);
            this.token = serializedToken.getData();
            this.tokenType = serializedToken.getType().getName();
        }
    }

    /**
     * Initializes a token entry from existing data.
     *
     * @param token     the serialized token
     * @param tokenType the serialized type of the token
     * @param timestamp the timestamp of the token
     * @param owner     the owner of the token
     */
    protected AbstractTokenEntry(T token, String tokenType, String timestamp, String owner) {
        this.token = token;
        this.tokenType = tokenType;
        this.timestamp = timestamp;
        this.owner = owner;
    }

    /**
     * Default constructor required for JPA
     */
    protected AbstractTokenEntry() {
    }

    /**
     * Returns the serialized token.
     *
     * @return the serialized token stored in this entry
     */
    public SerializedObject<T> getSerializedToken() {
        if (token == null) {
            return null;
        }
        return new SimpleSerializedObject<>(token, (Class<T>) token.getClass(), getTokenType());
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
     * Returns the storage timestamp of this token entry.
     *
     * @return The storage timestamp
     */
    public Instant timestamp() {
        return DateTimeUtils.parseInstant(timestamp);
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
     * Update this entry with the given {@code token}, serializing it using given {@code serializer}.
     *
     * @param token      The token to update the entry with
     * @param serializer The serializer to serialize data with
     */
    public abstract void updateToken(TrackingToken token, Serializer serializer);

    /**
     * Returns the name of the process to which this token belongs.
     *
     * @return the process name
     */
    public abstract String getProcessorName();

    /**
     * Returns the segment index of the process to which this token belongs.
     *
     * @return the segment index
     */
    public abstract int getSegment();

    /**
     * Attempt to claim ownership of this token. When successful, this method returns {@code true}, otherwise
     * {@code false}. When a claim fails, this token should not be used, as it is already being used in another process.
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
     * Returns the identifier of the process (JVM) having a claim on this token, or {@code null} if the token isn't
     * claimed.
     *
     * @return the process (JVM) that claimed this token
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Update the token data to the given {@code token}, using given {@code serializer} to serialize it to the given
     * {@code contentType}.
     *
     * @param token       The token representing the state to update to
     * @param serializer  The serializer to update token to
     * @param contentType The type of data to represent the serialized data in
     */
    protected final void updateToken(TrackingToken token, Serializer serializer, Class<T> contentType) {
        SerializedObject<T> serializedToken = serializer.serialize(token, contentType);
        this.token = serializedToken.getData();
        this.tokenType = serializedToken.getType().getName();
        this.timestamp = formatInstant(clock.instant());
    }
}

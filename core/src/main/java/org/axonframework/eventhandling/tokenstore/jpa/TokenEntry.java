/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.tokenstore.jpa;

import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.Serializer;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Objects;

/**
 * Implementation of a token entry that stores its serialized token as a byte array.
 *
 * @author Rene de Waele
 * @author Allard Buijze
 */
@Entity
@IdClass(TokenEntry.PK.class)
public class TokenEntry extends AbstractTokenEntry<byte[]> {

    /**
     * The clock used to persist timestamps in this entry. Defaults to UTC system time.
     */
    public static Clock clock = Clock.systemUTC();

    @Id
    private String processorName;
    @Id
    private int segment;

    @Basic(optional = false)
    private String timestamp;

    @Basic
    private String owner;

    /**
     * Initializes a new token entry for given {@code token}, {@code processorName} and {@code segment}. The given
     * {@code serializer} can be used to serialize the token before it is stored.
     *
     * @param token       The tracking token to store
     * @param processorName     The name of the processor to store this token for
     * @param segment     The segment index of the processor
     * @param serializer  The serializer to use when storing a serialized token
     */
    public TokenEntry(String processorName, int segment, TrackingToken token, Serializer serializer) {
        super(token, serializer, byte[].class);
        this.processorName = processorName;
        this.segment = segment;
        this.timestamp = clock.instant().toString();
    }

    /**
     * Default constructor for JPA
     */
    @SuppressWarnings("unused")
    protected TokenEntry() {
    }

    /**
     * Returns the storage timestamp of this token entry.
     *
     * @return The storage timestamp
     */
    public Instant timestamp() {
        return Instant.parse(timestamp);
    }

    @Override
    public String getProcessorName() {
        return processorName;
    }

    @Override
    public int getSegment() {
        return segment;
    }

    /**
     * Attempt to claim ownership of this token. When successful, this method returns {@code true}, otherwise
     * {@code false}. When a claim fails, this token should not be used, as it is already being used in another process.
     * <p>
     * If a claim exists, but it is older than given {@code claimTimeout}, the claim may be 'stolen'.
     *
     * @param owner        The name of the current node, to register as owner. This name must be unique for multiple instances
     *                     of the same logical processor
     * @param claimTimeout The time after which a claim may be 'stolen' from its current owner
     * @return {@code true} if the claim succeeded, otherwise false
     */
    public boolean claim(String owner, TemporalAmount claimTimeout) {
        if (this.owner == null
                || owner.equals(this.owner)
                || expired(claimTimeout)) {
            this.timestamp = clock.instant().toString();
            this.owner = owner;
        }

        return Objects.equals(this.owner, owner);
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
            this.timestamp = clock.instant().toString();
        }
        return this.owner == null;
    }

    /**
     * Update this entry with the given {@code token}, serializing it using given {@code serializer}.
     *
     * @param token      The token to update the entry with
     * @param serializer The serializer to serialize data with
     */
    public void updateToken(TrackingToken token, Serializer serializer) {
        super.updateToken(token, serializer, byte[].class);
        this.timestamp = clock.instant().toString();
    }

    /**
     * Returns the identifier of the process (JVM) having a claim on this token, or {@code null} if the token isn't claimed.
     *
     * @return the process (JVM) that claimed this token
     */
    public String getOwner() {
        return owner;
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
        protected PK() {
        }

        /**
         * Constructs a primary key for a TokenEntry
         * @param processorName The name of the processor
         * @param segment the index of the processing segment
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

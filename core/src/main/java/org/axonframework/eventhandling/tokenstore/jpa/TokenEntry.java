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

import javax.persistence.*;
import java.time.Instant;

/**
 * Implementation of a token entry that stores its serialized token as a byte array.
 *
 * @author Rene de Waele
 */
@Entity
@IdClass(TokenEntry.PK.class)
public class TokenEntry extends AbstractTokenEntry<byte[]> {

    @Id
    private String processName;
    @Id
    private int segment;

    @Basic(optional = false)
    private String timestamp;

    /**
     * Initializes a new token entry for given {@code token}, {@code process} and {@code segment}. The given {@code
     * serializer} can be used to serialize the token before it is stored.
     *
     * @param token       The tracking token to store
     * @param process     The name of the process to store this token for
     * @param segment     The segment index of the process
     * @param timestamp   The timestamp of the stored token
     * @param serializer  The serializer to use when storing a serialized token
     */
    public TokenEntry(String process, int segment, TrackingToken token, Instant timestamp, Serializer serializer) {
        super(token, serializer, byte[].class);
        this.processName = process;
        this.segment = segment;
        this.timestamp = timestamp.toString();
    }

    /**
     * Default constructor for JPA
     */
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
    public String getProcessName() {
        return processName;
    }

    @Override
    public int getSegment() {
        return segment;
    }
}

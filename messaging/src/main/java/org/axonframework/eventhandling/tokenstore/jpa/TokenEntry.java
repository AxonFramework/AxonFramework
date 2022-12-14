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

package org.axonframework.eventhandling.tokenstore.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.AbstractTokenEntry;
import org.axonframework.serialization.Serializer;

import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of a token entry compatible with JPA that stores its serialized token as a byte array.
 *
 * @author Rene de Waele
 * @author Allard Buijze
 */
@Entity
@IdClass(TokenEntry.PK.class)
public class TokenEntry extends AbstractTokenEntry<byte[]> {

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
        super(token, serializer, byte[].class);
        this.processorName = processorName;
        this.segment = segment;
    }

    /**
     * Default constructor for JPA
     */
    @SuppressWarnings("unused")
    protected TokenEntry() {
    }

    @Override
    public void updateToken(TrackingToken token, Serializer serializer) {
        updateToken(token, serializer, byte[].class);
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

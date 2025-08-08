/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageType;

import java.time.temporal.TemporalAccessor;

import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Entity
@Table(indexes = @Index(columnList = "aggregateIdentifier,aggregateSequenceNumber", unique = true))
public class AggregateBasedEventEntry {

    @Id
    @GeneratedValue
    @SuppressWarnings("unused")
    private long globalIndex;
    @Column(nullable = false, unique = true)
    private String identifier;
    @Basic(optional = false)
    private String type;
    @Basic
    private String version;
    @Basic(optional = false)
    @Lob
    @Column(length = 10000)
    private byte[] payload;
    @Basic
    @Lob
    @Column(length = 10000)
    private byte[] metadata;
    @Basic(optional = false)
    private String timestamp;
    @Basic
    private String aggregateType;
    @Basic
    private String aggregateIdentifier;
    @Basic
    private Long aggregateSequenceNumber;

    /**
     * Constructs an {@code AggregateBasedEventEntry} based on the given parameters.
     *
     * @param identifier              The identifier of the {@link EventMessage}.
     * @param type                    The {@link MessageType#name()} of an {@link EventMessage#type()}.
     * @param version                 The {@link MessageType#version()} of an {@link EventMessage#type()}.
     * @param payload                 The {@link EventMessage#payload()} as a {@code byte[]}.
     * @param metadata                The {@link EventMessage#metaData()} as a {@code byte[]}.
     * @param timestamp               The time at which the {@link EventMessage} was originally created.
     * @param aggregateType           The type of the aggregate that published this {@link EventMessage}. May be
     *                                {@code null} if the event does not originate from an aggregate.
     * @param aggregateIdentifier     The identifier of the aggregate that published this {@link EventMessage}. May be
     *                                {@code null} if the event does not originate from an aggregate.
     * @param aggregateSequenceNumber The sequence number of the {@link EventMessage} in the aggregate. May be
     *                                {@code null} if the event does not originate from an aggregate.
     */
    public AggregateBasedEventEntry(@Nonnull String identifier,
                                    @Nonnull String type,
                                    @Nonnull String version,
                                    @Nonnull byte[] payload,
                                    @Nonnull byte[] metadata,
                                    @Nonnull Object timestamp,
                                    @Nullable String aggregateType,
                                    @Nullable String aggregateIdentifier,
                                    @Nullable Long aggregateSequenceNumber) {
        this.identifier = identifier;
        this.type = type;
        this.version = version;
        this.payload = payload;
        this.metadata = metadata;
        this.timestamp = timestamp instanceof TemporalAccessor
                ? formatInstant((TemporalAccessor) timestamp)
                : timestamp.toString();
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
        this.aggregateSequenceNumber = aggregateSequenceNumber;
    }

    /**
     * Default constructor required by JPA.
     */
    protected AggregateBasedEventEntry() {
        //Default constructor required by JPA.
    }

    public long globalIndex() {
        return globalIndex;
    }

    public String identifier() {
        return identifier;
    }

    public String type() {
        return type;
    }

    public String version() {
        return version;
    }

    public byte[] payload() {
        return payload;
    }

    public byte[] metadata() {
        return metadata;
    }

    public String timestamp() {
        return timestamp;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateIdentifier() {
        return aggregateIdentifier;
    }

    public Long aggregateSequenceNumber() {
        return aggregateSequenceNumber;
    }
}

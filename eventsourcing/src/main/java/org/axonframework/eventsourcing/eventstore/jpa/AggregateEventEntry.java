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

package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.MessageType;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;

import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * A JPA entry dedicated for storing {@link EventMessage EventMessages} in the
 * {@link AggregateBasedJpaEventStorageEngine}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Entity
@Table(indexes = @Index(columnList = "aggregateIdentifier,aggregateSequenceNumber", unique = true))
public class AggregateEventEntry {

    // Deliberate field ordering for column ordering
    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "globalIndexGenerator"
    )
    @SequenceGenerator(
            name = "globalIndexGenerator",
            sequenceName = "aggregate-event-global-index-sequence",
            allocationSize = 1
    )
    private long globalIndex;
    private String aggregateType;
    private String aggregateIdentifier;
    private Long aggregateSequenceNumber;
    @Basic(optional = false)
    private String type;
    @Basic(optional = false)
    private String version;
    @Basic(optional = false)
    private String timestamp;
    @Basic(optional = false)
    @Lob
    private byte[] payload;
    @Lob
    private byte[] metadata;
    @Column(nullable = false, unique = true)
    private String identifier;

    /**
     * Constructor for a {@code AggregateEventEntry} when <b>appending</b> events, since the
     * {@link #globalIndex()} will be defined by the storage layer.
     *
     * @param identifier              The identifier of the {@link EventMessage}.
     * @param type                    The {@link MessageType#name()} of an {@link EventMessage#type()}.
     * @param version                 The {@link MessageType#version()} of an {@link EventMessage#type()}.
     * @param payload                 The {@link EventMessage#payload()} as a {@code byte[]}.
     * @param metadata                The {@link EventMessage#metadata()} as a {@code byte[]}.
     * @param timestamp               The time at which the {@link EventMessage} was originally created.
     * @param aggregateType           The type of the aggregate that published this {@link EventMessage}. May be
     *                                {@code null} if the event does not originate from an aggregate.
     * @param aggregateIdentifier     The identifier of the aggregate that published this {@link EventMessage}. May be
     *                                {@code null} if the event does not originate from an aggregate.
     * @param aggregateSequenceNumber The sequence number of the {@link EventMessage} in the aggregate. May be
     *                                {@code null} if the event does not originate from an aggregate.
     */
    public AggregateEventEntry(@Nonnull String identifier,
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
     * Constructor for an {@code AggregateEventEntry} when <b>reading</b> events, since all parameters should be
     * given.
     *
     * @param globalIndex             The position of the {@link EventMessage} in the event store.
     * @param identifier              The identifier of the {@link EventMessage}.
     * @param type                    The {@link MessageType#name()} of an {@link EventMessage#type()}.
     * @param version                 The {@link MessageType#version()} of an {@link EventMessage#type()}.
     * @param payload                 The {@link EventMessage#payload()} as a {@code byte[]}.
     * @param metadata                The {@link EventMessage#metadata()} as a {@code byte[]}.
     * @param timestamp               The time at which the {@link EventMessage} was originally created.
     * @param aggregateType           The type of the aggregate that published this {@link EventMessage}. May be
     *                                {@code null} if the event does not originate from an aggregate.
     * @param aggregateIdentifier     The identifier of the aggregate that published this {@link EventMessage}. May be
     *                                {@code null} if the event does not originate from an aggregate.
     * @param aggregateSequenceNumber The sequence number of the {@link EventMessage} in the aggregate. May be
     *                                {@code null} if the event does not originate from an aggregate.
     */
    public AggregateEventEntry(long globalIndex,
                               @Nonnull String identifier,
                               @Nonnull String type,
                               @Nonnull String version,
                               @Nonnull byte[] payload,
                               @Nonnull byte[] metadata,
                               @Nonnull String timestamp,
                               @Nullable String aggregateType,
                               @Nullable String aggregateIdentifier,
                               @Nullable Long aggregateSequenceNumber) {
        this.globalIndex = globalIndex;
        this.identifier = identifier;
        this.type = type;
        this.version = version;
        this.payload = payload;
        this.metadata = metadata;
        this.timestamp = timestamp;
        this.aggregateType = aggregateType;
        this.aggregateIdentifier = aggregateIdentifier;
        this.aggregateSequenceNumber = aggregateSequenceNumber;
    }

    /**
     * Default constructor required by JPA.
     */
    protected AggregateEventEntry() {
        //Default constructor required by JPA.
    }

    /**
     * Returns the global index of the stored {@link EventMessage}, reflecting its position in the event store.
     *
     * @return The global index of the stored {@link EventMessage}, reflecting its position in the event store.
     */
    public long globalIndex() {
        return globalIndex;
    }

    /**
     * Returns the {@link EventMessage#identifier() identifier} of the stored {@link EventMessage}.
     *
     * @return The {@link EventMessage#identifier() identifier} of the stored {@link EventMessage}.
     */
    @Nonnull
    public String identifier() {
        return identifier;
    }

    /**
     * Returns the {@link MessageType#name() type} of the stored {@link EventMessage}.
     *
     * @return The {@link MessageType#name() type} of the stored {@link EventMessage}.
     */
    @Nonnull
    public String type() {
        return type;
    }

    /**
     * Returns the {@link MessageType#version() version} of the stored {@link EventMessage}.
     *
     * @return The {@link MessageType#version() version} of the stored {@link EventMessage}.
     */
    @Nonnull
    public String version() {
        return version;
    }

    /**
     * Returns the {@link EventMessage#payload() payload} of the stored {@link EventMessage}.
     *
     * @return The {@link EventMessage#payload() payload} of the stored {@link EventMessage}.
     */
    @Nonnull
    public byte[] payload() {
        return payload;
    }

    /**
     * Returns the {@link EventMessage#metadata() metadata} of the stored {@link EventMessage}.
     *
     * @return The {@link EventMessage#metadata() metadata} of the stored {@link EventMessage}.
     */
    @Nonnull
    public byte[] metadata() {
        return metadata;
    }

    /**
     * Returns the {@link EventMessage#timestamp() timestamp} of the stored {@link EventMessage}.
     *
     * @return The {@link EventMessage#timestamp() timestamp} of the stored {@link EventMessage}.
     */
    @Nonnull
    public Instant timestamp() {
        return DateTimeUtils.parseInstant(timestamp);
    }

    /**
     * Returns the {@link EventMessage#timestamp() timestamp} of the stored {@link EventMessage} as a {@code String}.
     *
     * @return The {@link EventMessage#timestamp() timestamp} of the stored {@link EventMessage} as a {@code String}.
     */
    @Nonnull
    public String timestampAsString() {
        return timestamp;
    }

    /**
     * Returns the aggregate type of the stored {@link EventMessage}.
     * <p>
     * Will be {@code null} if the event did not originate from an aggregate.
     *
     * @return The aggregate type of the stored {@link EventMessage}.
     */
    @Nullable
    public String aggregateType() {
        return aggregateType;
    }

    /**
     * Returns the aggregateIdentifier of the stored {@link EventMessage}.
     * <p>
     * Will be {@code null} if the event did not originate from an aggregate.
     *
     * @return The aggregateIdentifier of the stored {@link EventMessage}.
     */
    @Nullable
    public String aggregateIdentifier() {
        return aggregateIdentifier;
    }

    /**
     * Returns the aggregate sequence number of the stored {@link EventMessage}.
     * <p>
     * Will be {@code null} if the event did not originate from an aggregate.
     *
     * @return The aggregate sequence number of the stored {@link EventMessage}.
     */
    @Nullable
    public Long aggregateSequenceNumber() {
        return aggregateSequenceNumber;
    }
}

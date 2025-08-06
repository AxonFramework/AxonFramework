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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import jakarta.persistence.MappedSuperclass;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Map;

import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * Abstract base class of a serialized event. Fields in this class contain JPA annotations that direct JPA event storage
 * engines how to store event entries.
 *
 * @param <P> The content type of the {@link #payload()}.
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0.0
 */
@MappedSuperclass
public abstract class AbstractEventEntry<P> implements EventData<P> {

    @Column(nullable = false, unique = true)
    private String eventIdentifier;
    @Basic(optional = false)
    private String payloadType;
    @Basic
    private String payloadRevision;
    @Basic(optional = false)
    @Lob
    @Column(length = 10000)
    private P payload;
    @Basic
    @Lob
    @Column(length = 10000)
    private P metaData;
    @Basic(optional = false)
    private String timeStamp;

    /**
     * Construct a new event entry from a published event message to enable storing the event or sending it to a remote
     * location.
     * <p>
     * The given {@code serializer} will be used to serialize the payload and metadata in the given
     * {@code eventMessage}. The type of the serialized data will be the same as the given {@code contentType}.
     *
     * @param eventMessage The event message to convert to a serialized event entry.
     * @param serializer   The serializer to convert the event.
     * @param contentType  The data type of the payload and metadata after serialization.
     * @deprecated In favor of the
     * {@link AbstractEventEntry#AbstractEventEntry(String, String, String, Object, Object, Object)} constructor.
     */
    @Deprecated
    public AbstractEventEntry(EventMessage<?> eventMessage, Serializer serializer, Class<P> contentType) {
        SerializedObject<P> payload = eventMessage.serializePayload(serializer, contentType);
        SerializedObject<P> metaData = eventMessage.serializeMetaData(serializer, contentType);
        this.eventIdentifier = eventMessage.identifier();
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
        this.payload = payload.getData();
        this.metaData = metaData.getData();
        this.timeStamp = formatInstant(eventMessage.timestamp());
    }

    /**
     * Constructs an {@code AbstractEventEntry} with the given parameters.
     *
     * @param eventIdentifier The identifier of the event.
     * @param payloadType     The fully qualified class name or alias of the event payload.
     * @param payloadRevision The revision of the event payload.
     * @param payload         The serialized payload.
     * @param metaData        The serialized metadata.
     * @param timestamp       The time at which the event was originally created.
     */
    public AbstractEventEntry(@Nonnull String eventIdentifier,
                              @Nonnull String payloadType,
                              @Nonnull String payloadRevision,
                              @Nullable P payload,
                              @Nullable P metaData,
                              @Nonnull Object timestamp) {
        this.eventIdentifier = eventIdentifier;
        this.payloadType = payloadType;
        this.payloadRevision = payloadRevision;
        this.payload = payload;
        this.metaData = metaData;
        this.timeStamp = timestamp instanceof TemporalAccessor
                ? formatInstant((TemporalAccessor) timestamp)
                : timestamp.toString();
    }

    /**
     * Default constructor required by JPA.
     */
    protected AbstractEventEntry() {
        // Default constructor required by JPA.
    }

    @Override
    @Nonnull
    public String eventIdentifier() {
        return eventIdentifier;
    }

    @Override
    @Nonnull
    public String type() {
        return payloadType;
    }

    @Override
    @Nonnull
    public String version() {
        return payloadRevision;
    }

    @Override
    @Nonnull
    public P payload() {
        return payload;
    }

    @Override
    @Nonnull
    public Map<String, String> metaData() {
        // TODO discuss what the storage format will be
        return Map.of();
    }

    @Override
    @Nonnull
    public Instant timestamp() {
        return DateTimeUtils.parseInstant(timeStamp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<P> getMetaData() {
        return new SerializedMetaData<>(metaData, (Class<P>) metaData.getClass());
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<P> getPayload() {
        return new SimpleSerializedObject<>(payload, (Class<P>) payload.getClass(),
                                            new SimpleSerializedType(payloadType, payloadRevision));
    }
}

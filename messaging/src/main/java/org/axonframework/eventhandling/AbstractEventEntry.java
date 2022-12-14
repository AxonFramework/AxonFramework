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

package org.axonframework.eventhandling;

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

import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * Abstract base class of a serialized event. Fields in this class contain JPA annotations that direct JPA event storage
 * engines how to store event entries.
 *
 * @author Rene de Waele
 */
@MappedSuperclass
public abstract class AbstractEventEntry<T> implements EventData<T> {

    @Column(nullable = false, unique = true)
    private String eventIdentifier;
    @Basic(optional = false)
    private String timeStamp;
    @Basic(optional = false)
    private String payloadType;
    @Basic
    private String payloadRevision;
    @Basic(optional = false)
    @Lob
    @Column(length = 10000)
    private T payload;
    @Basic
    @Lob
    @Column(length = 10000)
    private T metaData;

    /**
     * Construct a new event entry from a published event message to enable storing the event or sending it to a remote
     * location.
     * <p>
     * The given {@code serializer} will be used to serialize the payload and metadata in the given {@code eventMessage}.
     * The type of the serialized data will be the same as the given {@code contentType}.
     *
     * @param eventMessage The event message to convert to a serialized event entry
     * @param serializer   The serializer to convert the event
     * @param contentType  The data type of the payload and metadata after serialization
     */
    public AbstractEventEntry(EventMessage<?> eventMessage, Serializer serializer, Class<T> contentType) {
        SerializedObject<T> payload = eventMessage.serializePayload(serializer, contentType);
        SerializedObject<T> metaData = eventMessage.serializeMetaData(serializer, contentType);
        this.eventIdentifier = eventMessage.getIdentifier();
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
        this.payload = payload.getData();
        this.metaData = metaData.getData();
        this.timeStamp = formatInstant(eventMessage.getTimestamp());
    }

    /**
     * Reconstruct an event entry from a stored object.
     *
     * @param eventIdentifier The identifier of the event
     * @param timestamp       The time at which the event was originally created
     * @param payloadType     The fully qualified class name or alias of the event payload
     * @param payloadRevision The revision of the event payload
     * @param payload         The serialized payload
     * @param metaData        The serialized metadata
     */
    public AbstractEventEntry(String eventIdentifier, Object timestamp, String payloadType, String payloadRevision,
                              T payload, T metaData) {
        this.eventIdentifier = eventIdentifier;
        if (timestamp instanceof TemporalAccessor) {
            this.timeStamp = formatInstant((TemporalAccessor) timestamp);
        } else {
            this.timeStamp = timestamp.toString();
        }
        this.payloadType = payloadType;
        this.payloadRevision = payloadRevision;
        this.payload = payload;
        this.metaData = metaData;
    }

    /**
     * Default constructor required by JPA
     */
    protected AbstractEventEntry() {
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public Instant getTimestamp() {
        return DateTimeUtils.parseInstant(timeStamp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<T> getMetaData() {
        return new SerializedMetaData<>(metaData, (Class<T>) metaData.getClass());
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<T> getPayload() {
        return new SimpleSerializedObject<>(payload, (Class<T>) payload.getClass(),
                                            new SimpleSerializedType(payloadType, payloadRevision));
    }
}

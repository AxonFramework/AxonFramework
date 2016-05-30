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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serialization.*;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;

/**
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
    private T payload;
    @Basic
    @Lob
    private T metaData;

    public AbstractEventEntry(EventMessage<?> eventMessage, Serializer serializer, Class<T> contentType) {
        SerializedObject<T> payload = serializer.serialize(eventMessage.getPayload(), contentType);
        SerializedObject<T> metaData = serializer.serialize(eventMessage.getMetaData(), contentType);
        this.eventIdentifier = eventMessage.getIdentifier();
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
        this.payload = payload.getData();
        this.metaData = metaData.getData();
        this.timeStamp = eventMessage.getTimestamp().toString();
    }

    public AbstractEventEntry(String eventIdentifier, Object timestamp, String payloadType, String payloadRevision,
                              T payload, T metaData) {
        this.eventIdentifier = eventIdentifier;
        if (timestamp instanceof TemporalAccessor) {
            this.timeStamp = Instant.from((TemporalAccessor) timestamp).toString();
        } else {
            this.timeStamp = timestamp.toString();
        }
        this.payloadType = payloadType;
        this.payloadRevision = payloadRevision;
        this.payload = payload;
        this.metaData = metaData;
    }

    protected AbstractEventEntry() {
    }

    @Override
    public String getEventIdentifier() {
        return eventIdentifier;
    }

    @Override
    public Instant getTimestamp() {
        return Instant.parse(timeStamp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<T> getMetaData() {
        return new SerializedMetaData<>(metaData, (Class<T>) metaData.getClass());
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<T> getPayload() {
        return new SimpleSerializedObject<>(payload, (Class<T>) payload.getClass(), getPayloadType());
    }

    protected SerializedType getPayloadType() {
        return new SimpleSerializedType(payloadType, payloadRevision);
    }
}

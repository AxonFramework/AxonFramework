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

package org.axonframework.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serializer.*;

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
public abstract class AbstractEventEntry<T> implements SerializedEventData<T> {

    @Column(nullable = false, unique = true)
    private String eventIdentifier;
    @Basic(optional = false)
    private Long timeStamp;
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

    public AbstractEventEntry(EventMessage<?> eventMessage, Serializer serializer) {
        SerializedObject<T> payload = serializer.serialize(eventMessage.getPayload(), getContentType());
        SerializedObject<T> metaData = serializer.serialize(eventMessage.getMetaData(), getContentType());
        this.eventIdentifier = eventMessage.getIdentifier();
        this.timeStamp = eventMessage.getTimestamp().toEpochMilli();
        this.payloadType = payload.getType().getName();
        this.payloadRevision = payload.getType().getRevision();
        this.payload = payload.getData();
        this.metaData = metaData.getData();
    }

    public AbstractEventEntry(String eventIdentifier, Object timestamp, String payloadType, String payloadRevision,
                              T payload, T metaData) {
        this.eventIdentifier = eventIdentifier;
        if (timestamp instanceof TemporalAccessor) {
            this.timeStamp = Instant.from((TemporalAccessor) timestamp).toEpochMilli();
        } else if (timestamp instanceof CharSequence) {
            this.timeStamp = Instant.parse((CharSequence) timestamp).toEpochMilli();
        } else {
            this.timeStamp = (Long) timestamp;
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
        return Instant.ofEpochMilli(timeStamp);
    }

    @Override
    public SerializedObject<T> getMetaData() {
        return new SerializedMetaData<>(metaData, getContentType());
    }

    @Override
    public SerializedObject<T> getPayload() {
        return new SimpleSerializedObject<>(payload, getContentType(), getPayloadType());
    }

    protected SerializedType getPayloadType() {
        return new SimpleSerializedType(payloadType, payloadRevision);
    }

    protected abstract Class<T> getContentType();
}

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

package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.grpc.event.Event;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;

import java.time.Instant;
import java.util.Map;

/**
 * DomainEventData implementation that retrieves its raw data from a protobuf {@link Event} message.
 * <p>
 * This implementation strictly breaks the contract of DomainEventData, in that the returned SerializedObject for
 * MetaData does not contain the byte array representation of the metadata, but the {@link Event#getMetaDataMap()
 * MetaDataMap} defined in the protobuf message.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class GrpcBackedDomainEventData implements DomainEventData<byte[]> {

    private final Event event;

    /**
     * Initialize using the given {@code event} as the source of raw data.
     *
     * @param event The protobuf Event message containing the raw data
     */
    public GrpcBackedDomainEventData(Event event) {
        this.event = event;
    }

    @Override
    public String getType() {
        String aggregateType = event.getAggregateType();
        return "".equals(aggregateType) ? null : aggregateType;
    }

    @Override
    public String getAggregateIdentifier() {
        String aggregateIdentifier = event.getAggregateIdentifier();
        return "".equals(aggregateIdentifier) ? null : aggregateIdentifier;
    }

    @Override
    public long getSequenceNumber() {
        return event.getAggregateSequenceNumber();
    }

    @Override
    public String getEventIdentifier() {
        return event.getMessageIdentifier();
    }

    @Override
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(event.getTimestamp());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that this implementation breaks the contract of {@link DomainEventData#getMetaData()}, in that it doesn't
     * return the serialized data as a byte array, but as a {@link Event#getMetaDataMap() MetaDataMap}
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public SerializedObject getMetaData() {
        return new SerializedMetaData(event.getMetaDataMap(), Map.class);
    }

    @Override
    public SerializedObject<byte[]> getPayload() {
        String revision = event.getPayload().getRevision();
        return new SimpleSerializedObject<>(event.getPayload().getData().toByteArray(),
                                            byte[].class, event.getPayload().getType(),
                                            "".equals(revision) ? null : revision);
    }

    public boolean isSnapshot() {
        return event.getSnapshot();
    }
}

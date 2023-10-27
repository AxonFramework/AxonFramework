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

package org.axonframework.serialization.upcasting.event;

import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.EventData;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.CachingSupplier;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Implementation of an {@link IntermediateEventRepresentation} that contains the original serialized payload and
 * metadata before these have been upcast. Usually there is one {@link InitialEventRepresentation} per event entry from
 * the data store.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class InitialEventRepresentation implements IntermediateEventRepresentation {

    private final SerializedObject<Object> data;
    private final SerializedType type;
    private final Class<?> contentType;
    private final LazyDeserializingObject<MetaData> metaData;
    private final String eventIdentifier;
    private final Supplier<Instant> timestamp;

    //optionals
    private final String aggregateType;
    private final String aggregateIdentifier;
    private final Long sequenceNumber;
    private final TrackingToken trackingToken;

    private final Serializer serializer;

    /**
     * Initializes an {@link InitialEventRepresentation} from the given {@code eventData}. The provided {@code
     * serializer} is used to deserialize metadata if the metadata is required during upcasting. The serializer also
     * provides the {@link Converter} used to convert serialized data from one format to another if required by any
     * upcaster.
     *
     * @param eventData  the serialized event data
     * @param serializer the serializer to deserialize metadata and provide the converter factory
     */
    @SuppressWarnings("unchecked")
    public InitialEventRepresentation(EventData<?> eventData, Serializer serializer) {
        data = (SerializedObject<Object>) eventData.getPayload();
        type = data.getType();
        contentType = data.getContentType();
        metaData = new LazyDeserializingObject<>(eventData.getMetaData(), serializer);
        eventIdentifier = eventData.getEventIdentifier();
        timestamp = CachingSupplier.of(eventData::getTimestamp);
        if (eventData instanceof DomainEventData<?>) {
            DomainEventData<?> domainEventData = (DomainEventData<?>) eventData;
            aggregateType = domainEventData.getType();
            aggregateIdentifier = domainEventData.getAggregateIdentifier();
            sequenceNumber = domainEventData.getSequenceNumber();
        } else {
            aggregateType = null;
            aggregateIdentifier = null;
            sequenceNumber = null;
        }
        if (eventData instanceof TrackedEventData<?>) {
            trackingToken = ((TrackedEventData<?>) eventData).trackingToken();
        } else {
            trackingToken = null;
        }
        this.serializer = serializer;
    }

    @Override
    public <T> IntermediateEventRepresentation upcast(SerializedType outputType, Class<T> expectedRepresentationType,
                                                      Function<T, T> upcastFunction,
                                                      Function<MetaData, MetaData> metaDataUpcastFunction) {
        return new UpcastedEventRepresentation<>(outputType, this, upcastFunction, metaDataUpcastFunction,
                                                 expectedRepresentationType, serializer.getConverter());
    }

    @Override
    public SerializedType getType() {
        return type;
    }

    @Override
    public SerializedObject<?> getData() {
        return data;
    }

    @Override
    public <D> SerializedObject<D> getData(Class<D> requiredType) {
        return serializer.getConverter().convert(data, requiredType);
    }

    @Override
    public Class<?> getContentType() {
        return contentType;
    }

    @Override
    public String getMessageIdentifier() {
        return eventIdentifier;
    }

    @Override
    public Optional<String> getAggregateType() {
        return Optional.ofNullable(aggregateType);
    }

    @Override
    public Optional<String> getAggregateIdentifier() {
        return Optional.ofNullable(aggregateIdentifier);
    }

    @Override
    public Optional<Long> getSequenceNumber() {
        return Optional.ofNullable(sequenceNumber);
    }

    @Override
    public Optional<TrackingToken> getTrackingToken() {
        return Optional.ofNullable(trackingToken);
    }

    @Override
    public Instant getTimestamp() {
        return timestamp.get();
    }

    @Override
    public LazyDeserializingObject<MetaData> getMetaData() {
        return metaData;
    }

    @Override
    public boolean canConvertDataTo(Class<?> requiredType) {
        return serializer.getConverter().canConvert(data.getContentType(), requiredType);
    }
}

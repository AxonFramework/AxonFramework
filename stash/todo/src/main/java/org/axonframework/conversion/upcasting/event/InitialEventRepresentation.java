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

package org.axonframework.conversion.upcasting.event;

import org.axonframework.conversion.CachingSupplier;
import org.axonframework.conversion.ChainingContentTypeConverter;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.LazyDeserializingObject;
import org.axonframework.conversion.SerializedObject;
import org.axonframework.conversion.SerializedType;
import org.axonframework.conversion.Serializer;
import org.axonframework.conversion.SimpleSerializedObject;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.eventhandling.DomainEventData;
import org.axonframework.messaging.eventhandling.EventData;
import org.axonframework.messaging.eventhandling.TrackedEventData;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

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
    private final LazyDeserializingObject<Metadata> metadata;
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
        metadata = new LazyDeserializingObject<>(eventData.getMetadata(), serializer);
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
                                                      Function<Metadata, Metadata> metadataUpcastFunction) {
        return new UpcastedEventRepresentation<>(outputType, this, upcastFunction, metadataUpcastFunction,
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
        if (data.getContentType().equals(requiredType)) {
            return (SerializedObject<D>) data;
        }
        return new SimpleSerializedObject<>(serializer.getConverter().convert(data.getData(), requiredType),
                                            requiredType,
                                            data.getType());
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
    public LazyDeserializingObject<Metadata> getMetadata() {
        return metadata;
    }

    @Override
    public boolean canConvertDataTo(Class<?> requiredType) {
        Converter converter = serializer.getConverter();
        if (converter instanceof ChainingContentTypeConverter chainingConverter) {
            return chainingConverter.canConvert(data.getContentType(), requiredType);
        } else {
            return false;
        }
    }
}

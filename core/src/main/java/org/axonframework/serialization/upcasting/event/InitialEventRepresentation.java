/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization.upcasting.event;

import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.EventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Implementation of an {@link IntermediateEventRepresentation} that contains the original serialized payload and
 * metadata before these have been upcast. Usually there is one {@link InitialEventRepresentation} per event entry
 * from the data store.
 *
 * @author Rene de Waele
 */
public class InitialEventRepresentation implements IntermediateEventRepresentation {

    private final SerializedType outputType;
    private final SerializedObject<Object> outputData;
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
     * provides the {@link ConverterFactory} used to convert serialized data from one format to another if required
     * by any upcaster.
     *
     * @param eventData  the serialized event data
     * @param serializer the serializer to deserialize metadata and provide the converter factory
     */
    @SuppressWarnings("unchecked")
    public InitialEventRepresentation(EventData<?> eventData, Serializer serializer) {
        outputType = eventData.getPayload().getType();
        outputData = (SerializedObject<Object>) eventData.getPayload();
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
            trackingToken = ((TrackedEventData) eventData).trackingToken();
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
                                                 expectedRepresentationType, serializer.getConverterFactory());
    }

    @Override
    public SerializedType getOutputType() {
        return outputType;
    }

    @Override
    public SerializedObject<?> getOutputData() {
        return outputData;
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
}

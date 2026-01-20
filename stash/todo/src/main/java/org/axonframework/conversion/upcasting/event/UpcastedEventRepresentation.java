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

import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.LazyDeserializingObject;
import org.axonframework.conversion.SerializedObject;
import org.axonframework.conversion.SerializedType;
import org.axonframework.conversion.SimpleSerializedObject;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * Implementation of an {@link IntermediateEventRepresentation} that contains upcast functions for the payload and
 * metadata of a previous representation. Note that the upcast functions are to go from one representation to another
 * (never to more than one). In other words, the upcast functions stored in the UpcastedEventRepresentation are not
 * mapping one to one to the upcast method of an upcaster.
 *
 * @param <T> the required type of the serialized data. If the data is not of this type the representation uses a {@link
 *            Converter} to convert to the required type.
 * @author Rene de Waele
 * @since 3.0
 */
public class UpcastedEventRepresentation<T> implements IntermediateEventRepresentation {

    private final SerializedType outputType;
    private final IntermediateEventRepresentation source;
    private final Function<T, T> upcastFunction;
    private final Function<Metadata, Metadata> metadataUpcastFunction;
    private final Class<T> requiredType;
    private final Converter converter;
    private LazyDeserializingObject<Metadata> metadata;

    /**
     * Initializes an {@link UpcastedEventRepresentation} from source data and given upcast functions for payload and
     * metadata. The given {@code converter} is used to convert to the serialized data format required by the upcast
     * functions.
     *
     * @param outputType             the output type of the payload data after upcasting
     * @param source                 the intermediate representation that will be upcast
     * @param upcastFunction         the function to upcast the payload data
     * @param metadataUpcastFunction the function to upcast the metadata
     * @param requiredType           the type that is needed for the upcastFunction
     * @param converter              produces converters to convert the serialized data type if required
     */
    public UpcastedEventRepresentation(SerializedType outputType, IntermediateEventRepresentation source,
                                       Function<T, T> upcastFunction,
                                       Function<Metadata, Metadata> metadataUpcastFunction, Class<T> requiredType,
                                       Converter converter) {
        this.outputType = outputType;
        this.source = source;
        this.upcastFunction = upcastFunction;
        this.metadataUpcastFunction = metadataUpcastFunction;
        this.requiredType = requiredType;
        this.converter = converter;
    }

    @Override
    public <S> IntermediateEventRepresentation upcast(SerializedType outputType, Class<S> expectedRepresentationType,
                                                      Function<S, S> upcastFunction,
                                                      Function<Metadata, Metadata> metadataUpcastFunction) {
        return new UpcastedEventRepresentation<>(outputType, this, upcastFunction, metadataUpcastFunction,
                                                 expectedRepresentationType, converter);
    }

    @Override
    public SerializedType getType() {
        return outputType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<T> getData() {
        SerializedObject<?> sourceData = source.getData();
        SerializedObject<?> serializedInput;
        if (sourceData.getContentType().equals(requiredType)) {
            serializedInput = sourceData;
        } else {
            serializedInput = new SimpleSerializedObject<>(converter.convert(sourceData.getData(), requiredType),
                                                           requiredType,
                                                           sourceData.getType());
        }
        return new SimpleSerializedObject<>(upcastFunction.apply((T) serializedInput.getData()),
                                            requiredType,
                                            getType());
    }

    @Override
    public <D> SerializedObject<D> getData(Class<D> requiredType) {
        SerializedObject<T> sourceData = getData();
        if (sourceData.getContentType().equals(requiredType)) {
            return (SerializedObject<D>) sourceData;
        }
        return new SimpleSerializedObject<>(converter.convert(sourceData.getData(), requiredType),
                                            requiredType,
                                            sourceData.getType());
    }

    @Override
    public Class<?> getContentType() {
        return requiredType;
    }

    @Override
    public String getMessageIdentifier() {
        return source.getMessageIdentifier();
    }

    @Override
    public Optional<String> getAggregateType() {
        return source.getAggregateType();
    }

    @Override
    public Optional<String> getAggregateIdentifier() {
        return source.getAggregateIdentifier();
    }

    @Override
    public Optional<Long> getSequenceNumber() {
        return source.getSequenceNumber();
    }

    @Override
    public Optional<TrackingToken> getTrackingToken() {
        return source.getTrackingToken();
    }

    @Override
    public Instant getTimestamp() {
        return source.getTimestamp();
    }

    @Override
    public LazyDeserializingObject<Metadata> getMetadata() {
        if (metadata == null) {
            metadata = new LazyDeserializingObject<>(metadataUpcastFunction.apply(source.getMetadata().getObject()));
        }
        return metadata;
    }

    @Override
    public boolean canConvertDataTo(Class<?> requiredType) {
        return converter.canConvert(source.getData().getContentType(), requiredType);
    }
}

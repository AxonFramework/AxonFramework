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

package org.axonframework.serialization.upcasting.event;

import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.serialization.*;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author Rene de Waele
 */
public class UpcastedEventRepresentation<T> implements IntermediateEventRepresentation {

    private final SerializedType outputType;
    private final IntermediateEventRepresentation source;
    private final Function<T, T> upcastFunction;
    private final Function<MetaData, MetaData> metaDataUpcastFunction;
    private final Class<T> expectedType;
    private final ConverterFactory converterFactory;
    private LazyDeserializingObject<MetaData> metaData;

    public UpcastedEventRepresentation(SerializedType outputType, IntermediateEventRepresentation source,
                                       Function<T, T> upcastFunction,
                                       Function<MetaData, MetaData> metaDataUpcastFunction, Class<T> expectedType,
                                       ConverterFactory converterFactory) {
        this.outputType = outputType;
        this.source = source;
        this.upcastFunction = upcastFunction;
        this.metaDataUpcastFunction = metaDataUpcastFunction;
        this.expectedType = expectedType;
        this.converterFactory = converterFactory;
    }

    @Override
    public <S> IntermediateEventRepresentation upcast(SerializedType outputType, Class<S> expectedRepresentationType,
                                                      Function<S, S> upcastFunction,
                                                      Function<MetaData, MetaData> metaDataUpcastFunction) {
        return new UpcastedEventRepresentation<>(outputType, this, upcastFunction, metaDataUpcastFunction,
                                                 expectedRepresentationType, converterFactory);
    }

    @Override
    public SerializedType getOutputType() {
        return outputType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SerializedObject<T> getOutputData() {
        SerializedObject<T> serializedInput =
                converterFactory.getConverter(source.getOutputData().getContentType(), expectedType)
                        .convert((SerializedObject) source.getOutputData());
        T result = upcastFunction.apply(serializedInput.getData());
        return new SimpleSerializedObject<>(result, expectedType, getOutputType());
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
    public LazyDeserializingObject<MetaData> getMetaData() {
        if (metaData == null) {
            metaData = new LazyDeserializingObject<>(metaDataUpcastFunction.apply(source.getMetaData().getObject()));
        }
        return metaData;
    }
}

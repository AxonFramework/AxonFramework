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

import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * Interface describing the intermediate representation of an event during upcasting.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public interface IntermediateEventRepresentation {

    /**
     * Upcast the serialized payload of the event (leaving other aspects of the event the same).
     *
     * @param outputType                 The output type of the event after upcasting
     * @param expectedRepresentationType The type of the serialized payload required by the upcast function
     * @param upcastFunction             The upcast function for the event's payload
     * @param <T>                        The expected payload type before and after upcasting
     * @return The intermediate representation of the event after upcasting
     */
    default <T> IntermediateEventRepresentation upcastPayload(SerializedType outputType,
                                                              Class<T> expectedRepresentationType,
                                                              Function<T, T> upcastFunction) {
        return upcast(outputType, expectedRepresentationType, upcastFunction, Function.identity());
    }

    /**
     * Upcast the serialized payload of the event (leaving other aspects of the event the same).
     *
     * @param outputType                 The output type of the event after upcasting
     * @param expectedRepresentationType The type of the serialized payload required by the upcast function
     * @param upcastFunction             The upcast function for the event's payload
     * @param metaDataUpcastFunction     The upcast function for the event's metadata
     * @param <T>                        The expected payload type before and after upcasting
     * @return The intermediate representation of the event after upcasting
     */
    <T> IntermediateEventRepresentation upcast(SerializedType outputType, Class<T> expectedRepresentationType,
                                               Function<T, T> upcastFunction,
                                               Function<MetaData, MetaData> metaDataUpcastFunction);

    /**
     * Returns the type and version of the data contained in this representation.
     *
     * @return the type and version of the represented data
     */
    SerializedType getType();

    /**
     * Get the data of this representation. The type of the returned data should match that of {@link #getType()}.
     *
     * @return the data representation of the object
     */
    SerializedObject<?> getData();

    /**
     * Get the data of this representation. The type of the returned data will be converted to the given {@code
     * requiredType}.
     *
     * @param requiredType the type to convert to
     * @return the data representation of the object converted to the required type
     */
    <D> SerializedObject<D> getData(Class<D> requiredType);

    /**
     * Returns the type of this representation's {@link #getData() data}.
     *
     * @return The type of this representation's {@link #getData() data}.
     */
    default Class<?> getContentType() {
        return getData().getContentType();
    }

    /**
     * Returns the identifier of the message wrapping the object to upcast.
     *
     * @return the identifier of the message wrapping the object to upcast
     */
    String getMessageIdentifier();

    /**
     * Returns the Type of the Aggregate to which the Event owning the object to upcast, was applied. This will return
     * an empty Optional if the object being upcast was not contained in a DomainEventMessage.
     *
     * @return the Type of the Aggregate to which the Event was applied, or an empty Optional if not applicable
     */
    Optional<String> getAggregateType();

    /**
     * Returns the Identifier of the Aggregate to which the Event owning the object to upcast, was applied. This will
     * return an empty Optional if the object being upcast was not contained in a DomainEventMessage.
     *
     * @return the Identifier of the Aggregate to which the Event was applied, or an empty Optional if not applicable
     */
    Optional<String> getAggregateIdentifier();

    /**
     * Returns the sequence number of the event in the aggregate, or an empty Optional if the message wrapping the
     * object being upcast does not contain a sequence number.
     *
     * @return the sequence number of the event in the aggregate, or an empty Optional if not applicable
     */
    Optional<Long> getSequenceNumber();

    /**
     * Returns the tracking token of the event, or an empty Optional if the message wrapping the object being upcast
     * does not contain a tracking token.
     *
     * @return the tracking token of the event, or an empty Optional if not applicable
     */
    Optional<TrackingToken> getTrackingToken();

    /**
     * Returns the timestamp at which the event was first created. Will return {@code null} if the object being upcast
     *
     * @return the timestamp at which the event was first created, if available
     */
    Instant getTimestamp();

    /**
     * Returns the meta data of the message wrapping the object being upcast. If the meta data is not available, or is
     * in fact the subject of being upcast itself, this method returns {@code null}.
     *
     * @return the MetaData of the message wrapping the object to upcast, if available
     */
    LazyDeserializingObject<MetaData> getMetaData();

    /**
     * Checks if the data can be converted to the given {@code requiredType}.
     *
     * @param requiredType the type to validate if the contained data can be converted to.
     * @return true, if the intermediate representation's data can be converted to desired type, false otherwise
     */
    boolean canConvertDataTo(Class<?> requiredType);
}

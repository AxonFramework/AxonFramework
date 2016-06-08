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
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author Rene de Waele
 */
public interface IntermediateEventRepresentation {

    default <T> IntermediateEventRepresentation upcastPayload(SerializedType outputType,
                                                              Class<T> expectedRepresentationType,
                                                              Function<T, T> upcastFunction) {
        return upcast(outputType, expectedRepresentationType, upcastFunction, Function.identity());
    }

    <T> IntermediateEventRepresentation upcast(SerializedType outputType, Class<T> expectedRepresentationType,
                                               Function<T, T> upcastFunction,
                                               Function<MetaData, MetaData> metaDataUpcastFunction);

    /**
     * Returns the type and version of the data contained in this representation.
     *
     * @return the type and version of the represented data
     */
    SerializedType getOutputType();

    /**
     * Get the data of this representation. The type of the returned data should match that of {@link
     * #getOutputType()}.
     *
     * @return the data representation of the object
     */
    SerializedObject<?> getOutputData();

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
     * Returns the timestamp at which the event was first created. Will return <code>null</code> if the object being
     * upcast
     *
     * @return the timestamp at which the event was first created, if available
     */
    Instant getTimestamp();

    /**
     * Returns the meta data of the message wrapping the object being upcast. If the meta data is not available, or is
     * in fact the subject of being upcast itself, this method returns <code>null</code>.
     *
     * @return the MetaData of the message wrapping the object to upcast, if available
     */
    LazyDeserializingObject<MetaData> getMetaData();

}

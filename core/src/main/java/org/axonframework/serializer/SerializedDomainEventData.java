/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.serializer;

import java.time.ZonedDateTime;

/**
 * Interface describing the properties of serialized Domain Event Messages. Event Store implementations should have
 * their storage entries implement this interface.
 *
 * @param <T> The content type of the serialized data
 * @author Allard Buijze
 * @see org.axonframework.upcasting.UpcastSerializedDomainEventData
 * @since 2.0
 */
public interface SerializedDomainEventData<T> {

    /**
     * Returns the identifier of the serialized event.
     *
     * @return the identifier of the serialized event
     */
    String getEventIdentifier();

    /**
     * Returns the Identifier of the Aggregate to which the Event was applied.
     *
     * @return the Identifier of the Aggregate to which the Event was applied
     */
    Object getAggregateIdentifier();

    /**
     * Returns the sequence number of the event in the aggregate.
     *
     * @return the sequence number of the event in the aggregate
     */
    long getSequenceNumber();

    /**
     * Returns the timestamp at which the event was first created.
     *
     * @return the timestamp at which the event was first created
     */
    ZonedDateTime getTimestamp();

    /**
     * Returns the serialized data of the MetaData of the serialized Event.
     *
     * @return the serialized data of the MetaData of the serialized Event
     */
    SerializedObject<T> getMetaData();

    /**
     * Returns the serialized data of the Event Message's payload.
     *
     * @return the serialized data of the Event Message's payload
     */
    SerializedObject<T> getPayload();
}

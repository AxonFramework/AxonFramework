/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.upcasting;

import org.axonframework.domain.MetaData;
import org.axonframework.serializer.LazyDeserializingObject;
import org.axonframework.serializer.SerializedDomainEventData;
import org.axonframework.serializer.Serializer;
import org.joda.time.DateTime;

/**
 * Implementation of UpcastingContext that builds a context based on the information of a serialized domain event
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializedDomainEventUpcastingContext implements UpcastingContext {

    private final String messageIdentifier;
    private final Object aggregateIdentifier;
    private final Long sequenceNumber;
    private final DateTime timestamp;
    private final LazyDeserializingObject<MetaData> serializedMetaData;

    /**
     * Initializes the UpcastingContext using given serialized <code>domainEventData</code>. Deserialization of the
     * meta data contained in the <code>domainEventData</code> is done on first access of the meta data.
     *
     * @param domainEventData the object containing information about the domain event from which an object is being
     *                        upcast
     * @param serializer      The serializer that can be used to deserialize the meta data
     */
    public SerializedDomainEventUpcastingContext(SerializedDomainEventData domainEventData, Serializer serializer) {
        this.messageIdentifier = domainEventData.getEventIdentifier();
        this.aggregateIdentifier = domainEventData.getAggregateIdentifier();
        this.sequenceNumber = domainEventData.getSequenceNumber();
        this.timestamp = domainEventData.getTimestamp();
        this.serializedMetaData = new LazyDeserializingObject<MetaData>(domainEventData.getMetaData(), serializer);
    }

    @Override
    public String getMessageIdentifier() {
        return messageIdentifier;
    }

    @Override
    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public DateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public MetaData getMetaData() {
        return serializedMetaData.getObject();
    }

    /**
     * Returns the wrapper containing the serialized MetaData.
     *
     * @return the wrapper containing the serialized MetaData
     */
    public LazyDeserializingObject<MetaData> getSerializedMetaData() {
        return serializedMetaData;
    }
}

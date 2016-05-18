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

package org.axonframework.eventstore.legacy;

import org.axonframework.eventstore.SerializedTrackedEventData;
import org.axonframework.eventstore.TrackingToken;

/**
 * @author Rene de Waele
 */
public class GenericLegacyDomainEventEntry<T> extends AbstractLegacyDomainEventEntry<T> implements SerializedTrackedEventData<T> {
    public GenericLegacyDomainEventEntry(String type, String aggregateIdentifier, long sequenceNumber,
                                         String eventIdentifier, Object timeStamp, String payloadType,
                                         String payloadRevision, T payload, T metaData) {
        super(type, aggregateIdentifier, sequenceNumber, eventIdentifier, timeStamp, payloadType, payloadRevision,
              payload, metaData);
    }

    @Override
    public TrackingToken trackingToken() {
        return new LegacyTrackingToken(getTimestamp(), getAggregateIdentifier(), getSequenceNumber());
    }
}

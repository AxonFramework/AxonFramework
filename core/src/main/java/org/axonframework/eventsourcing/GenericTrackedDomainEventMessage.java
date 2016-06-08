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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.messaging.Message;

import java.time.Instant;
import java.util.Map;

/**
 * @author Rene de Waele
 */
public class GenericTrackedDomainEventMessage<T> extends GenericDomainEventMessage<T> implements TrackedEventMessage<T> {
    private final TrackingToken trackingToken;

    public GenericTrackedDomainEventMessage(TrackingToken trackingToken, DomainEventMessage<T> delegate) {
        this(trackingToken, delegate.getType(), delegate.getAggregateIdentifier(), delegate.getSequenceNumber(),
             delegate, delegate.getTimestamp());
    }

    public GenericTrackedDomainEventMessage(TrackingToken trackingToken, String type, String aggregateIdentifier,
                                             long sequenceNumber, Message<T> delegate, Instant timestamp) {
        super(type, aggregateIdentifier, sequenceNumber, delegate, timestamp);
        this.trackingToken = trackingToken;
    }

    @Override
    public TrackingToken trackingToken() {
        return trackingToken;
    }

    @Override
    public GenericTrackedDomainEventMessage<T> withMetaData(Map<String, ?> metaData) {
        return new GenericTrackedDomainEventMessage<>(trackingToken, getType(), getAggregateIdentifier(),
                                                      getSequenceNumber(), getDelegate().withMetaData(metaData),
                                                      getTimestamp());
    }

    @Override
    public GenericTrackedDomainEventMessage<T> andMetaData(Map<String, ?> metaData) {
        return new GenericTrackedDomainEventMessage<>(trackingToken, getType(), getAggregateIdentifier(),
                                                      getSequenceNumber(), getDelegate().andMetaData(metaData),
                                                      getTimestamp());
    }
}

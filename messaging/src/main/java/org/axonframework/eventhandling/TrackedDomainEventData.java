/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.serialization.SerializedObject;

import java.time.Instant;
import java.util.Map;

/**
 * Specialization of the DomainEventData class that includes the Token representing the position of this event in a
 * stream.
 *
 * @param <T> The content type of the serialized data.
 */
@Deprecated // TODO discuss if we can remove this
public class TrackedDomainEventData<T> implements TrackedEventData<T>, DomainEventData<T> {

    private final TrackingToken trackingToken;
    private final DomainEventData<T> eventData;

    /**
     * Initialize the TrackingDomainEventData with given {@code trackingToken} and {@code domainEventEntry}.
     *
     * @param trackingToken    The token representing this event's position in a stream
     * @param domainEventEntry The entry containing the event data itself
     */
    public TrackedDomainEventData(TrackingToken trackingToken, DomainEventData<T> domainEventEntry) {
        this.trackingToken = trackingToken;
        this.eventData = domainEventEntry;
    }

    @Override
    public TrackingToken trackingToken() {
        return trackingToken;
    }

    @Override
    @Nonnull
    public String getEventIdentifier() {
        return eventData.getEventIdentifier();
    }

    @Override
    @Nonnull
    public String type() {
        return eventData.type();
    }

    @Override
    @Nonnull
    public String version() {
        return eventData.version();
    }

    @Override
    @Nonnull
    public T payload() {
        return eventData.payload();
    }

    @Override
    @Nonnull
    public Map<String, String> metaData() {
        return eventData.metaData();
    }

    @Override
    @Nonnull
    public Instant getTimestamp() {
        return eventData.getTimestamp();
    }

    @Override
    public String getType() {
        return eventData.getType();
    }

    @Override
    public String getAggregateIdentifier() {
        return eventData.getAggregateIdentifier();
    }

    @Override
    public long getSequenceNumber() {
        return eventData.getSequenceNumber();
    }

    @Override
    public SerializedObject<T> getMetaData() {
        return eventData.getMetaData();
    }

    @Override
    public SerializedObject<T> getPayload() {
        return eventData.getPayload();
    }
}

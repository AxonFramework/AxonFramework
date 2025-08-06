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
 * @param <P> The content type of the serialized data.
 */
@Deprecated // TODO discuss if we can remove this
public class TrackedDomainEventData<P> implements TrackedEventData<P>, DomainEventData<P> {

    private final TrackingToken trackingToken;
    private final DomainEventData<P> eventData;

    /**
     * Initialize the TrackingDomainEventData with given {@code trackingToken} and {@code domainEventEntry}.
     *
     * @param trackingToken    The token representing this event's position in a stream
     * @param domainEventEntry The entry containing the event data itself
     */
    public TrackedDomainEventData(TrackingToken trackingToken, DomainEventData<P> domainEventEntry) {
        this.trackingToken = trackingToken;
        this.eventData = domainEventEntry;
    }

    @Override
    public TrackingToken trackingToken() {
        return trackingToken;
    }

    @Override
    @Nonnull
    public String eventIdentifier() {
        return eventData.eventIdentifier();
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
    public P payload() {
        return eventData.payload();
    }

    @Override
    @Nonnull
    public Map<String, String> metaData() {
        return eventData.metaData();
    }

    @Override
    @Nonnull
    public Instant timestamp() {
        return eventData.timestamp();
    }

    @Override
    @Nonnull
    public String aggregateType() {
        return eventData.aggregateType();
    }

    @Override
    @Nonnull
    public String aggregateIdentifier() {
        return eventData.aggregateIdentifier();
    }

    @Override
    public long aggregateSequenceNumber() {
        return eventData.aggregateSequenceNumber();
    }

    @Override
    public SerializedObject<P> getMetaData() {
        return eventData.getMetaData();
    }

    @Override
    public SerializedObject<P> getPayload() {
        return eventData.getPayload();
    }
}

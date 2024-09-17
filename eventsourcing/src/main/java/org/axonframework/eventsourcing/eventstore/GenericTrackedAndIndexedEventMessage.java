/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MetaData;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// TODO #3129 - MessageStream allows Pair<TrackingToken, EventMessage> type - Remove this class.
public class GenericTrackedAndIndexedEventMessage<P> implements IndexedEventMessage<P>, TrackedEventMessage<P> {

    private final EventMessage<P> delegate;
    private final TrackingToken token;
    private final Set<Index> indices;

    public GenericTrackedAndIndexedEventMessage(EventMessage<P> delegate,
                                                TrackingToken token,
                                                Set<Index> indices) {
        this.delegate = delegate;
        this.token = token;
        this.indices = indices;
    }

    @Override
    public String getIdentifier() {
        return this.delegate.getIdentifier();
    }

    @Override
    public MetaData getMetaData() {
        return this.delegate.getMetaData();
    }

    @Override
    public P getPayload() {
        return this.delegate.getPayload();
    }

    @Override
    public Class<P> getPayloadType() {
        return this.delegate.getPayloadType();
    }

    @Override
    public Instant getTimestamp() {
        return this.delegate.getTimestamp();
    }

    @Override
    public TrackingToken trackingToken() {
        return this.token;
    }

    @Override
    public Set<Index> indices() {
        return this.indices;
    }

    @Override
    public EventMessage<P> withMetaData(Map<String, ?> metaData) {
        return getMetaData().equals(metaData)
                ? this
                : new GenericTrackedAndIndexedEventMessage<>(this.delegate.withMetaData(metaData),
                                                             this.token,
                                                             this.indices);
    }

    @Override
    public EventMessage<P> andMetaData(Map<String, ?> metaData) {
        return metaData.isEmpty() || getMetaData().equals(metaData)
                ? this
                : new GenericTrackedAndIndexedEventMessage<>(this.delegate.andMetaData(metaData),
                                                             this.token,
                                                             this.indices);
    }

    @Override
    public TrackedEventMessage<P> withTrackingToken(TrackingToken trackingToken) {
        return new GenericTrackedAndIndexedEventMessage<>(this.delegate, trackingToken, this.indices);
    }

    @Override
    public IndexedEventMessage<P> updateIndices(Function<Set<Index>, Set<Index>> updater) {
        return new GenericTrackedAndIndexedEventMessage<>(this.delegate, this.token, updater.apply(this.indices));
    }
}

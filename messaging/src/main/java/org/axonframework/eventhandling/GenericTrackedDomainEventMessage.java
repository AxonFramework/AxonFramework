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

package org.axonframework.eventhandling;

import org.axonframework.messaging.Message;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Generic implementation of a {@link DomainEventMessage} that is also a {@link TrackedEventMessage}.
 *
 * @param <T> The type of payload contained in this Message
 */
public class GenericTrackedDomainEventMessage<T> extends GenericDomainEventMessage<T> implements
                                                                                      TrackedEventMessage<T> {
    private static final long serialVersionUID = 6211645167637822558L;
    private final TrackingToken trackingToken;

    /**
     * Initialize a DomainEventMessage originating from an aggregate.
     *
     * @param trackingToken Tracking token of the event
     * @param delegate      Delegate domain event containing other event data
     */
    public GenericTrackedDomainEventMessage(TrackingToken trackingToken, DomainEventMessage<T> delegate) {
        this(trackingToken, delegate.getType(), delegate.getAggregateIdentifier(), delegate.getSequenceNumber(),
             delegate, delegate.getTimestamp());
    }

    /**
     * Initialize a DomainEventMessage originating from an Aggregate using existing data. The timestamp of the event is
     * supplied lazily to prevent unnecessary deserialization of the timestamp.
     *
     * @param trackingToken       Tracking token of the event
     * @param type                The domain type
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param delegate            The delegate message providing the payload, metadata and identifier of the event
     * @param timestamp           The event's timestamp supplier
     */
    public GenericTrackedDomainEventMessage(TrackingToken trackingToken, String type, String aggregateIdentifier,
                                            long sequenceNumber, Message<T> delegate, Supplier<Instant> timestamp) {
        super(type, aggregateIdentifier, sequenceNumber, delegate, timestamp);
        this.trackingToken = trackingToken;
    }

    /**
     * Initialize a DomainEventMessage originating from an aggregate.
     *
     * @param trackingToken       Tracking token of the event
     * @param type                The domain type
     * @param aggregateIdentifier The identifier of the aggregate generating this message
     * @param sequenceNumber      The message's sequence number
     * @param delegate            The delegate message providing the payload, metadata and identifier of the event
     * @param timestamp           The event's timestamp
     */
    protected GenericTrackedDomainEventMessage(TrackingToken trackingToken, String type, String aggregateIdentifier,
                                               long sequenceNumber, Message<T> delegate, Instant timestamp) {
        super(type, aggregateIdentifier, sequenceNumber, delegate, timestamp);
        this.trackingToken = trackingToken;
    }

    @Override
    public TrackingToken trackingToken() {
        return trackingToken;
    }

    @Override
    public GenericTrackedDomainEventMessage<T> withMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericTrackedDomainEventMessage<>(trackingToken, getType(), getAggregateIdentifier(),
                                                      getSequenceNumber(), getDelegate().withMetaData(metaData),
                                                      getTimestamp());
    }

    @Override
    public GenericTrackedDomainEventMessage<T> andMetaData(@Nonnull Map<String, ?> metaData) {
        return new GenericTrackedDomainEventMessage<>(trackingToken, getType(), getAggregateIdentifier(),
                                                      getSequenceNumber(), getDelegate().andMetaData(metaData),
                                                      getTimestamp());
    }

    @Override
    protected void describeTo(StringBuilder stringBuilder) {
        super.describeTo(stringBuilder);
        stringBuilder.append(", trackingToken={")
                     .append(trackingToken())
                     .append('}');
    }

    @Override
    public GenericTrackedDomainEventMessage<T> withTrackingToken(TrackingToken trackingToken) {
        return new GenericTrackedDomainEventMessage<>(trackingToken, this);
    }

    @Override
    protected String describeType() {
        return "GenericTrackedDomainEventMessage";
    }
}

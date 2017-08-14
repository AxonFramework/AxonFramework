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

package org.axonframework.eventsourcing.eventstore;

/**
 * Generic implementation of a serialized domain event entry with tracking token. This implementation can be used by any
 * {@link EventStorageEngine} to reconstruct an event from the underlying storage.
 *
 * @author Rene de Waele
 * @deprecated Use {@link TrackedDomainEventData} instead, as it uses composition (instead of inheritance)
 */
@Deprecated
public class GenericTrackedDomainEventEntry<T> extends AbstractDomainEventEntry<T> implements TrackedEventData<T> {

    private final TrackingToken trackingToken;

    /**
     * Reconstruct an event entry from a stored object.
     *
     * @param trackingToken       The tracking token of the event entry
     * @param type                The type of aggregate that published this event
     * @param aggregateIdentifier The identifier of the aggregate that published this event
     * @param sequenceNumber      The sequence number of the event in the aggregate
     * @param eventIdentifier     The identifier of the event
     * @param timestamp           The time at which the event was originally created
     * @param payloadType         The fully qualified class name or alias of the event payload
     * @param payloadRevision     The revision of the event payload
     * @param payload             The serialized payload
     * @param metaData            The serialized metadata
     */
    public GenericTrackedDomainEventEntry(TrackingToken trackingToken, String type, String aggregateIdentifier,
                                          long sequenceNumber, String eventIdentifier, Object timestamp,
                                          String payloadType, String payloadRevision, T payload, T metaData) {
        super(type, aggregateIdentifier, sequenceNumber, eventIdentifier, timestamp, payloadType, payloadRevision,
              payload, metaData);
        this.trackingToken = trackingToken;
    }

    /**
     * Reconstruct an event entry from a stored object. The given {@code globalSequence} is converted into a {@link
     * GlobalSequenceTrackingToken}.
     *
     * @param globalSequence      The global sequence number of the event entry
     * @param type                The type of aggregate that published this event
     * @param aggregateIdentifier The identifier of the aggregate that published this event
     * @param sequenceNumber      The sequence number of the event in the aggregate
     * @param eventIdentifier     The identifier of the event
     * @param timestamp           The time at which the event was originally created
     * @param payloadType         The fully qualified class name or alias of the event payload
     * @param payloadRevision     The revision of the event payload
     * @param payload             The serialized payload
     * @param metaData            The serialized metadata
     */
    public GenericTrackedDomainEventEntry(long globalSequence, String type, String aggregateIdentifier,
                                          long sequenceNumber, String eventIdentifier, Object timestamp,
                                          String payloadType, String payloadRevision, T payload, T metaData) {
        super(type, aggregateIdentifier, sequenceNumber, eventIdentifier, timestamp, payloadType, payloadRevision,
              payload, metaData);
        this.trackingToken = new GlobalSequenceTrackingToken(globalSequence);
    }

    @Override
    public TrackingToken trackingToken() {
        return trackingToken;
    }
}

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

import org.axonframework.commandhandling.model.ConcurrencyException;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.DefaultEventUpcasterChain;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventStorageEngine implements EventStorageEngine {

    private Serializer serializer;
    private EventUpcasterChain upcasterChain = new DefaultEventUpcasterChain();
    private PersistenceExceptionResolver persistenceExceptionResolver;

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        Stream<? extends TrackedEventData<?>> input = readEventData(trackingToken, mayBlock);
        return EventUtils.upcastAndDeserializeTrackedEvents(input, serializer, upcasterChain, false);
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber) {
        Stream<? extends DomainEventData<?>> input = readEventData(aggregateIdentifier, firstSequenceNumber);
        return DomainEventStream
                .of(EventUtils.upcastAndDeserializeDomainEvents(input, serializer, upcasterChain, false).iterator());
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(String aggregateIdentifier) {
        return readSnapshotData(aggregateIdentifier).map(entry -> EventUtils
                .upcastAndDeserializeDomainEvents(Stream.of(entry), serializer, upcasterChain, false).findFirst()
                .orElse(null));
    }

    @Override
    public void appendEvents(List<? extends EventMessage<?>> events) {
        appendEvents(events, serializer);
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        storeSnapshot(snapshot, serializer);
    }

    protected void handlePersistenceException(Exception exception, EventMessage<?> failedEvent) {
        String eventDescription;
        if (failedEvent instanceof DomainEventMessage<?>) {
            DomainEventMessage<?> failedDomainEvent = (DomainEventMessage<?>) failedEvent;
            eventDescription =
                    format("An event for aggregate [%s] at sequence [%d]", failedDomainEvent.getAggregateIdentifier(),
                           failedDomainEvent.getSequenceNumber());
        } else {
            eventDescription = format("An event with identifier [%s]", failedEvent.getIdentifier());
        }
        if (persistenceExceptionResolver != null && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
            throw new ConcurrencyException(eventDescription + " was already inserted", exception);
        } else {
            throw new EventStoreException(eventDescription + " could not be persisted", exception);
        }
    }

    protected abstract void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer);

    protected abstract void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer);

    protected abstract Stream<? extends DomainEventData<?>> readEventData(String identifier, long firstSequenceNumber);

    protected abstract Stream<? extends TrackedEventData<?>> readEventData(TrackingToken trackingToken,
                                                                           boolean mayBlock);

    protected abstract Optional<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier);

    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Sets the UpcasterChain which allow older revisions of serialized objects to be deserialized.
     *
     * @param upcasterChain the upcaster chain providing the upcasting capabilities
     */
    public void setUpcasterChain(EventUpcasterChain upcasterChain) {
        this.upcasterChain = upcasterChain;
    }

    /**
     * Sets the persistenceExceptionResolver that will help detect concurrency exceptions from the backing database.
     *
     * @param persistenceExceptionResolver the persistenceExceptionResolver that will help detect concurrency
     *                                     exceptions
     */
    public void setPersistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
        this.persistenceExceptionResolver = persistenceExceptionResolver;
    }

}

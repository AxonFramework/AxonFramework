package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.MessageStream;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;


/**
 * EventStorageEngine implementation that uses JPA to store and fetch events.
 * <p>
 * By default, the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class LegacyJpaEventStorageEngine implements AsyncEventStorageEngine {

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(new NoOpAppendTransaction(condition));
        }
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Not implemented yet"));
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        return null;
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        return null;
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        return null;
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        return null;
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return null;
    }

    @Override
    public void describeTo(@javax.annotation.Nonnull ComponentDescriptor descriptor) {

    }

    // todo: better name?
    private record NoOpAppendTransaction(AppendCondition appendCondition) implements AppendTransaction {

        @Override
        public CompletableFuture<ConsistencyMarker> commit() { // todo: not sure about that from(appendCondition)
            return CompletableFuture.completedFuture(AggregateBasedConsistencyMarker.from(appendCondition));
        }

        @Override
        public void rollback() {

        }
    }
}

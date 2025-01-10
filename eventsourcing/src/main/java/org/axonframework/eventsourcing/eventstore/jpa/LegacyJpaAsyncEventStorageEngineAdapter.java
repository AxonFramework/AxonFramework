package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
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

public class LegacyJpaAsyncEventStorageEngineAdapter implements AsyncEventStorageEngine {

    private final JpaEventStorageEngine legacy;

    public LegacyJpaAsyncEventStorageEngineAdapter(JpaEventStorageEngine legacy) {
        this.legacy = legacy;
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {
        return CompletableFuture.runAsync(() -> new AppendTransaction() {

            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                return null;
            }

            @Override
            public void rollback() {

            }
        });
    }

    private static EventMessage<?> toEventMessage(TaggedEventMessage<?> taggedEventMessage) {
        throw new RuntimeException("Not implemented yet!");
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
}

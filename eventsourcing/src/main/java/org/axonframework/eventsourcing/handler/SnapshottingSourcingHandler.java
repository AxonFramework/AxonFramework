/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.eventsourcing.handler;

import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.snapshot.api.EvolutionResult;
import org.axonframework.eventsourcing.snapshot.api.Snapshot;
import org.axonframework.eventsourcing.snapshot.api.SnapshotPolicy;
import org.axonframework.eventsourcing.snapshot.api.Snapshotter;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link SourcingHandler} implementation that reconstructs an entity using snapshots
 * when available, falling back to a full sourcing of events from the {@link EventStore}
 * if necessary.
 * <p>
 * This handler first attempts to load a snapshot of the entity via the provided {@link Snapshotter}.
 * If a snapshot is present, reconstruction starts from the snapshot's state and position, reducing
 * the number of events that need to be replayed. If loading the snapshot fails or none exists,
 * the entity is fully reconstructed from the beginning of its event stream.
 * <p>
 * All events retrieved from the {@link EventStore} are applied sequentially using the
 * provided {@link InitializingEntityEvolver}. After sourcing completes, a snapshot is created
 * if the {@link SnapshotPolicy} indicated one was needed.
 *
 * @param <I> the type of the entity identifier
 * @param <E> the type of the entity
 * @since 5.1.0
 * @author John Hendrikx
 */
@Internal
public class SnapshottingSourcingHandler<I, E> implements SourcingHandler<I, E> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshottingSourcingHandler.class);

    private final EventStore eventStore;
    private final CriteriaResolver<I> criteriaResolver;
    private final MessageType messageType;
    private final Snapshotter<I, E> snapshotter;
    private final SnapshotPolicy snapshotPolicy;

    /**
     * Creates a new {@code SnapshottingSourcingHandler}.
     *
     * @param eventStore the {@link EventStore} used to source events, cannot be {@code null}
     * @param criteriaResolver the resolver to use to create the {@link EventCriteria} for sourcing, cannot be {@code null}
     * @param messageType the {@link MessageType}, cannot be {@code null}
     * @param snapshotPolicy the {@link SnapshotPolicy}, cannot be {@code null}
     * @param snapshotter the {@link Snapshotter} used to load and handle snapshots, cannot be {@code null}
     * @throws NullPointerException when a non-null argument was {@code null}
     */
    public SnapshottingSourcingHandler(
        EventStore eventStore,
        CriteriaResolver<I> criteriaResolver,
        MessageType messageType,
        SnapshotPolicy snapshotPolicy,
        Snapshotter<I, E> snapshotter
    ) {
        this.eventStore = Objects.requireNonNull(eventStore, "The eventStore parameter must not be null.");
        this.criteriaResolver = Objects.requireNonNull(criteriaResolver, "The criteriaResolver parameter must not be null.");
        this.messageType = Objects.requireNonNull(messageType, "The messageType parameter must not be null.");
        this.snapshotPolicy = Objects.requireNonNull(snapshotPolicy, "The snapshotPolicy parameter must not be null.");
        this.snapshotter = Objects.requireNonNull(snapshotter, "The snapshotter parameter must not be null.");
    }

    @Override
    public CompletableFuture<E> source(I identifier, InitializingEntityEvolver<I, E> evolver, ProcessingContext pc) {
        EventCriteria criteria = criteriaResolver.resolve(identifier, pc);
        long startTime = System.currentTimeMillis();

        return snapshotter.load(identifier)
            .exceptionally(e -> {
                LOGGER.warn("Snapshot loading failed, falling back to full reconstruction for: {} ({})", messageType, identifier, e);

                return null;  // indicates no snapshot, should trigger full reconstruction in next step
            })
            .thenCompose(snapshot -> sourceAndEvolve(identifier, snapshot, criteria, startTime, evolver, pc));
    }

    private CompletableFuture<E> sourceAndEvolve(
        I identifier,
        @Nullable Snapshot snapshot,
        EventCriteria criteria,
        long startTime,
        InitializingEntityEvolver<I, E> evolver,
        ProcessingContext pc
    ) {
        @SuppressWarnings("unchecked")
        E initialEntity = snapshot == null ? null : (E) snapshot.payload();
        Position startPosition = snapshot == null ? Position.START : snapshot.position();
        EventStoreTransaction transaction = eventStore.transaction(pc);
        SourcingCondition sourcingCondition = SourcingCondition.conditionFor(startPosition, criteria);
        AtomicReference<Position> postionRef = new AtomicReference<>(startPosition);
        AtomicInteger evolutionCount = new AtomicInteger();
        AtomicBoolean triggerSnapshot = new AtomicBoolean();
        MessageStream<? extends EventMessage> source = transaction.source(sourcingCondition, postionRef::set);

        return source
            .reduce(initialEntity, (entity, entry) -> {
                E evolvedEntity = evolver.evolve(identifier, entity, entry.message(), pc);

                evolutionCount.incrementAndGet();

                if (!triggerSnapshot.get() && snapshotPolicy.shouldSnapshot(entry.message())) {
                    triggerSnapshot.set(true);
                }

                return evolvedEntity;
            })
            .thenApply(entity -> {
                int ec = evolutionCount.get();

                if (ec > 0) {
                    Duration sourcingTime = Duration.ofMillis(Math.max(0, System.currentTimeMillis() - startTime));

                    // Snapshot is made when specifically triggered by an event, or based on the statistics:
                    if (triggerSnapshot.get() || snapshotPolicy.shouldSnapshot(new EvolutionResult(ec, sourcingTime))) {
                        snapshotter.store(identifier, entity, postionRef.get());
                    }
                }

                return entity;
            });
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventStore", eventStore);
        descriptor.describeProperty("criteriaResolver", criteriaResolver);
        descriptor.describeProperty("messageType", messageType);
        descriptor.describeProperty("snapshotPolicy", snapshotPolicy);
    }
}

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

package org.axonframework.eventsourcing.snapshot;

import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.snapshot.api.EntityLoadStatistics;
import org.axonframework.eventsourcing.snapshot.api.Snapshot;
import org.axonframework.eventsourcing.snapshot.api.SnapshotContext;
import org.axonframework.eventsourcing.snapshot.api.SnapshotPolicy;
import org.axonframework.eventsourcing.snapshot.api.SnapshotStore;
import org.axonframework.eventsourcing.snapshot.api.Snapshotter;
import org.axonframework.messaging.core.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link Snapshotter} implementation that persists snapshots to a {@link SnapshotStore}.
 * <p>
 * This snapshotter tracks the latest snapshots for event-sourced entities and stores them
 * asynchronously based on a {@link SnapshotPolicy}.
 * <p>
 * Features:
 * <ul>
 *     <li>Loads the latest snapshot from the {@link SnapshotStore} when starting an entity load.</li>
 *     <li>Keeps a temporary in-flight cache to prevent multiple concurrent snapshot operations
 *         for the same entity.</li>
 *     <li>Stores new snapshots asynchronously when the {@link SnapshotPolicy} indicates
 *         a snapshot is needed.</li>
 *     <li>Logs snapshot failures without blocking or failing entity evolution.</li>
 * </ul>
 *
 * <p><strong>Thread-safety:</strong> This class is safe to use concurrently for multiple
 * entities. Each entity’s in-flight snapshot is tracked independently.</p>
 *
 * <p><strong>Usage notes:</strong>
 * <ul>
 *     <li>Snapshots are an optimization; failures in storing a snapshot do not prevent
 *         the entity from being fully evolved from events.</li>
 *     <li>The in-flight cache is cleared automatically once the asynchronous store completes
 *         (unless replaced by a newer snapshot concurrently).</li>
 * </ul>
 *
 * @param <I> the type of the entity identifier
 * @param <E> the type of the entity being snapshot
 * @author John Hendrikx
 * @since 5.1.0
 */
public class StoreBackedSnapshotter<I, E> implements Snapshotter<I, E> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreBackedSnapshotter.class);

    private final SnapshotStore store;
    private final MessageType type;
    private final SnapshotPolicy snapshotPolicy;
    private final ConcurrentHashMap<I, Snapshot> inFlightSnapshots = new ConcurrentHashMap<>();

    /**
     * Creates a new instance.
     *
     * @param store a {@link SnapshotStore} used to store snapshots, cannot be {@code null}
     * @param type a versioned type to distinguish the snapshot in the store, cannot be {@code null}
     * @param snapshotPolicy a {@link SnapshotPolicy} which determines when to create a new snapshot, cannot be {@code null}
     * @throws NullPointerException when any argument is {@code null}
     */
    public StoreBackedSnapshotter(SnapshotStore store, MessageType type, SnapshotPolicy snapshotPolicy) {
        this.store = Objects.requireNonNull(store, "store");
        this.type = Objects.requireNonNull(type, "type");
        this.snapshotPolicy = Objects.requireNonNull(snapshotPolicy, "snapshotPolicy");
    }

    @Override
    public CompletableFuture<SnapshotContext<E>> start(I identifier) {
        Objects.requireNonNull(identifier, "identifier");

        Snapshot inFlightSnapshot = inFlightSnapshots.get(identifier);
        CompletableFuture<Snapshot> snapshotFuture = inFlightSnapshot == null
            ? store.load(type, identifier)
            : CompletableFuture.completedFuture(inFlightSnapshot);

        return snapshotFuture.thenApply(snapshot -> new InternalSnapshotContext(identifier, snapshot));
    }

    private final class InternalSnapshotContext implements SnapshotContext<E> {
        final long startTime = System.currentTimeMillis();
        final I identifier;
        final Snapshot snapshot;

        InternalSnapshotContext(I identifier, Snapshot snapshot) {
            this.identifier = identifier;
            this.snapshot = snapshot;
        }

        @Override
        public Snapshot snapshot() {
            return snapshot;
        }

        @Override
        public void entityEvolved(E entity, Position position, long evolveStepCount) {
            EntityLoadStatistics loadStatistics = new EntityLoadStatistics(
                evolveStepCount,
                Duration.ofMillis(Math.max(0, System.currentTimeMillis() - startTime))
            );

            if (snapshotPolicy.needsSnapshot(loadStatistics)) {
                Snapshot newSnapshot = new Snapshot(position, entity);

                inFlightSnapshots.put(identifier, newSnapshot);
                store.store(type, identifier, newSnapshot).whenComplete((voidResult, ex) -> {
                    // note: only remove inflight snapshot from cache if not replaced concurrently
                    inFlightSnapshots.remove(identifier, newSnapshot);

                    if (ex != null) {
                        LOGGER.warn("Snapshotting failed for {} with identifier {}", type, identifier, ex);
                    }
                });
            }
        }
    }
}

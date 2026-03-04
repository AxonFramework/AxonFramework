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

package org.axonframework.eventsourcing.snapshot.store;

import org.axonframework.conversion.Converter;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.snapshot.api.EvolutionResult;
import org.axonframework.eventsourcing.snapshot.api.Snapshot;
import org.axonframework.eventsourcing.snapshot.api.Snapshotter;
import org.axonframework.messaging.core.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link Snapshotter} implementation that persists snapshots to a {@link SnapshotStore}.
 * <p>
 * This snapshotter tracks snapshots of event-sourced entities and decides when to persist them
 * based on signals received during entity evolution. It uses an {@link EvolutionResult} and a
 * {@link SnapshotPolicy} to determine whether a snapshot should be created after sourcing is complete.</p>
 * <p>
 * <strong>Behavior:</strong>
 * <ul>
 *     <li>Loads the latest snapshot from the {@link SnapshotStore} when an entity is loaded.</li>
 *     <li>Stores new snapshots asynchronously when either the user requested a snapshot
 *         or the {@link SnapshotPolicy} indicates a snapshot is needed.</li>
 *     <li>Logs any snapshotting failures without blocking or affecting entity evolution.</li>
 * </ul>
 * <p>
 * <strong>Thread-safety:</strong> This class is safe to use concurrently for multiple entities.</p>
 * <p>
 * <strong>Usage notes:</strong>
 * <ul>
 *     <li>Snapshots are an optimization. If snapshot storage fails, the entity will still be
 *         fully evolved from its events.</li>
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
    private final Converter converter;
    private final Class<?> entityType;
    private final SnapshotPolicy snapshotPolicy;
    private final ConcurrentHashMap<I, Snapshot> inFlightSnapshots = new ConcurrentHashMap<>();

    /**
     * Creates a new instance.
     *
     * @param store a {@link SnapshotStore} used to store snapshots, cannot be {@code null}
     * @param type a versioned type to distinguish the snapshot in the store, cannot be {@code null}
     * @param converter a {@link Converter} used to deserialize snapshots, cannot be {@code null}
     * @param entityType the entity type, cannot be {@code null}
     * @param snapshotPolicy a {@link SnapshotPolicy} which determines when to create a new snapshot, cannot be {@code null}
     * @throws NullPointerException when any argument is {@code null}
     */
    public StoreBackedSnapshotter(
        SnapshotStore store,
        MessageType type,
        Converter converter,
        Class<E> entityType,
        SnapshotPolicy snapshotPolicy
    ) {
        this.store = Objects.requireNonNull(store, "The store parameter must not be null.");
        this.type = Objects.requireNonNull(type, "The type parameter must not be null.");
        this.converter = Objects.requireNonNull(converter, "The converter parameter must not be null.");
        this.entityType = Objects.requireNonNull(entityType, "The entityType parameter must not be null.");
        this.snapshotPolicy = Objects.requireNonNull(snapshotPolicy, "The snapshotPolicy parameter must not be null.");
    }

    @Override
    public CompletableFuture<Snapshot> load(I identifier) {
        Objects.requireNonNull(identifier, "The identifier parameter must not be null.");

        Snapshot inFlightSnapshot = inFlightSnapshots.get(identifier);
        CompletableFuture<Snapshot> loadedSnapshot = inFlightSnapshot == null
            ? store.load(type.qualifiedName(), identifier)
            : CompletableFuture.completedFuture(inFlightSnapshot);

        return loadedSnapshot
            .thenApply(snapshot -> {
                if (snapshot != null) {
                    if (snapshot.version().equals(type.version())) {
                        return entityType.isInstance(snapshot.payload())
                            ? snapshot
                            : snapshot.payload(converter.convert(snapshot.payload(), entityType));
                    }

                    // Version mismatched, ignore this snapshot (until there is version handling support)
                    LOGGER.info("Unsupported snapshot version. Snapshot of {} for identifier {} had unsupported version: {}", type, identifier, snapshot.version());
                }

                return null;
            });
    }

    @Override
    public void onEvolutionCompleted(
        I identifier,
        E entity,
        Position position,
        EvolutionResult evolutionResult
    ) {
        Objects.requireNonNull(identifier, "The identifier parameter must not be null.");
        Objects.requireNonNull(entity, "The entity parameter must not be null.");
        Objects.requireNonNull(position, "The position parameter must not be null.");
        Objects.requireNonNull(evolutionResult, "The evolutionResult parameter must not be null.");

        if (snapshotPolicy.needsSnapshot(evolutionResult)) {
            Snapshot newSnapshot = new Snapshot(position, type.version(), entity, Instant.now(), Map.of());

            inFlightSnapshots.put(identifier, newSnapshot);
            store.store(type.qualifiedName(), identifier, newSnapshot).whenComplete((voidResult, ex) -> {
                // note: only remove inflight snapshot from cache if not replaced concurrently
                inFlightSnapshots.remove(identifier, newSnapshot);

                if (ex != null) {
                    LOGGER.warn("Snapshotting failed for {} with identifier {}", type, identifier, ex);
                }
            });
        }
    }
}

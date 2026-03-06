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

package org.axonframework.eventsourcing.snapshot.api;

import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.messaging.eventhandling.EventMessage;

import java.util.concurrent.CompletableFuture;

/**
 * A {@code Snapshotter} is responsible for managing snapshots of event-sourced entities.
 * <p>
 * Its primary responsibilities are:
 * <ul>
 *     <li>Retrieve the current snapshot for a given entity (if any).</li>
 *     <li>Observe the evolution of an entity as events are applied during replay, allowing the snapshotter
 *         to decide whether a snapshot should be created at the end of replay.</li>
 *     <li>Persist a snapshot if it determines one should be created, based on the signals received
 *         during evolution.</li>
 * </ul>
 * <p>
 * The snapshotter does not manage metrics or control sourcing; it only reacts to evolution signals and
 * decides if and when a snapshot should be created.</p>
 *
 * @param <I> the type of the entity identifier
 * @param <E> the type of the entity being snapshotted
 * @author John Hendrikx
 * @since 5.1.0
 */
public interface Snapshotter<I, E> {

    /**
     * A {@link Snapshotter} which never loads snapshots and never creates
     * snapshots.
     */
    static final Snapshotter<?, ?> NO_SNAPSHOTTER = new Snapshotter<>() {
        @Override
        public CompletableFuture<Snapshot> load(Object identifier) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onEvolutionCompleted(
            Object identifier,
            Object entity,
            Position position,
            EvolutionResult loadStatistics
        ) {}
    };

    /**
     * Creates a {@link Snapshotter} which never loads snapshots and never creates
     * snapshots.
     *
     * @param <I> the type of the entity identifier
     * @param <E> the type of the entity being snapshotted
     * @return a {@link Snapshotter}, never {@code null}
     */
    @SuppressWarnings("unchecked")
    static <I, E> Snapshotter<I, E> noSnapshotter() {
        return (Snapshotter<I, E>) NO_SNAPSHOTTER;
    }

    /**
     * Asynchronously retrieves the snapshot for the given entity, if one exists.
     * <p>
     * The returned {@link CompletableFuture} completes with the snapshot, or {@code null} if none exists
     * or the snapshot cannot be used. It may complete exceptionally if snapshot retrieval fails.
     *
     * @param identifier the identifier of the entity, cannot be {@code null}
     * @return a {@link CompletableFuture} that completes with the entity's snapshot or {@code null}
     * @throws NullPointerException if {@code identifier} is {@code null}
     */
    CompletableFuture<Snapshot> load(I identifier);

    /**
     * Called after an event has been applied to the entity while sourcing it to its current state.
     * <p>
     * Implementations can use this callback to request a snapshot of the fully sourced entity
     * once the sourcing is complete. The boolean return value is a latching signal:
     * returning {@code true} indicates a snapshot should be taken at the end of sourcing.
     *
     * @param identifier the identifier of the entity, cannot be {@code null}
     * @param entity the entity state after the event has been applied, cannot be {@code null}
     * @param event the event that was just applied, cannot be {@code null}
     * @return {@code true} to request a snapshot at the end of sourcing, {@code false} otherwise
     * @throws NullPointerException if any argument is {@code null}
     */
    default boolean onEventApplied(I identifier, E entity, EventMessage event) {
        return false;
    }

    /**
     * Called immediately after an entity has been fully sourced from its snapshot and any subsequent events.
     * <p>
     * Implementations can use this callback to decide whether to create and persist a snapshot of the entity.
     * At this point, the entity has reached its current state, and all evolution metrics are available
     * in {@link EvolutionResult}. This method should not block; any I/O should be performed asynchronously.
     *
     * @param identifier the identifier of the entity, cannot be {@code null}
     * @param entity the fully sourced entity state, cannot be {@code null}
     * @param position the last processed position in the event stream, cannot be {@code null}
     * @param evolutionResult the metrics and signals collected during sourcing, cannot be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    void onEvolutionCompleted(
        I identifier,
        E entity,
        Position position,
        EvolutionResult evolutionResult
    );
}
package org.axonframework.eventsourcing.snapshot.api;

import java.util.concurrent.CompletableFuture;

/**
 * A {@code Snapshotter} is responsible for managing snapshot state of event-sourced entities.
 * <p>
 * Its main responsibilities are:
 * <ul>
 *     <li>Provide the current snapshot for a given entity (if any).</li>
 *     <li>Track and handle the evolution of the entity after replaying events.</li>
 * </ul>
 *
 * @param <I> the type of the entity identifier
 * @param <E> the type of the entity being snapshot
 * @author John Hendrikx
 * @since 5.1.0
 */
public interface Snapshotter<I, E> {

    /**
     * Asynchronously creates a snapshot lifecycle context for a given entity.
     * <p>
     * This method returns a {@link CompletableFuture} that, when completed, provides a
     * {@link SnapshotContext} for the entity. The context:
     * <ul>
     *     <li>Provides access to the current snapshot, if any, once the future completes</li>
     *     <li>Tracks the evolution lifecycle for the entity</li>
     *     <li>Allows the {@link SnapshotContext#entityEvolved} method to be called once evolution is complete</li>
     * </ul>
     * <p>
     * The returned future may complete exceptionally if snapshot retrieval fails.
     *
     * @param identifier the identifier of the entity; must not be {@code null}
     * @return a {@link CompletableFuture} that completes with a {@link SnapshotContext} object, never {@code null}
     * @throws NullPointerException if {@code identifier} is {@code null}
     */
    CompletableFuture<SnapshotContext<E>> start(I identifier);
}
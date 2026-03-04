/**
 * Public API for snapshotting of event-sourced entities.
 * <p>
 * This package contains the interfaces and records that define the contract
 * for snapshot management, independent of any particular storage or implementation.
 * <p>
 * Users can implement their own {@link org.axonframework.eventsourcing.snapshot.api.Snapshotter}
 * or rely on default implementations in the {@code store} package.
 *
 * <ul>
 *     <li>{@link org.axonframework.eventsourcing.snapshot.api.Snapshotter} - the main interface for managing snapshots</li>
 *     <li>{@link org.axonframework.eventsourcing.snapshot.api.Snapshot} - represents a snapshot of an entity at a specific position</li>
 *     <li>{@link org.axonframework.eventsourcing.snapshot.api.EvolutionResult} - metrics collected during entity sourcing</li>
 * </ul>
 */
@org.jspecify.annotations.NullMarked
package org.axonframework.eventsourcing.snapshot.api;
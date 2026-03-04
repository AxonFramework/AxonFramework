/**
 * In-memory snapshot store implementations.
 * <p>
 * This package contains concrete, ready-to-use {@link org.axonframework.eventsourcing.snapshot.store.SnapshotStore}
 * implementations that store snapshots in memory. These implementations are useful for:
 * <ul>
 *     <li>Testing and prototypes</li>
 *     <li>Single-node or ephemeral environments</li>
 * </ul>
 * <p>
 * Production-grade persistence should use stores provided in other modules (JDBC, JPA, AxonServer, etc.).
 * This package depends on {@code snapshot.store} and implements the {@link SnapshotStore} interface.
 */
@org.jspecify.annotations.NullMarked
package org.axonframework.eventsourcing.snapshot.inmemory;
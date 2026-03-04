/**
 * Store-backed snapshotting implementations for event-sourced entities.
 * <p>
 * This package contains the default {@link org.axonframework.eventsourcing.snapshot.api.Snapshotter}
 * implementation that persists snapshots to a {@link SnapshotStore}, along with supporting interfaces
 * such as {@link SnapshotStore} and {@link SnapshotPolicy}.
 * <p>
 * Users can:
 * <ul>
 *     <li>Use {@link StoreBackedSnapshotter} as the default snapshotter</li>
 *     <li>Provide their own {@link SnapshotStore} implementation</li>
 *     <li>Provide custom {@link SnapshotPolicy} implementations to control snapshot creation</li>
 * </ul>
 * <p>
 * This package is self-contained and modular. It builds on the {@code api} package but is
 * independent of concrete storage backends, which are provided in other packages or modules.
 */
@org.jspecify.annotations.NullMarked
package org.axonframework.eventsourcing.snapshot.store;
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
 *     <li>Provide custom {@link org.axonframework.eventsourcing.snapshot.api.SnapshotPolicy} implementations
 *         to control snapshot creation</li>
 * </ul>
 * <p>
 * This package is self-contained and modular. It builds on the {@code api} package but is
 * independent of concrete storage backends, which are provided in other packages or modules.
 */
@NullMarked
package org.axonframework.eventsourcing.snapshot.store;

import org.jspecify.annotations.NullMarked;

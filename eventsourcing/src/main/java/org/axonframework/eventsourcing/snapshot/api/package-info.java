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
@NullMarked
package org.axonframework.eventsourcing.snapshot.api;

import org.jspecify.annotations.NullMarked;

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

import org.axonframework.common.annotation.Internal;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * A {@code Snapshotter} is responsible for loading and storing snapshots of event-sourced entities.
 *
 * @param <I> the type of the entity identifier
 * @param <E> the type of the entity being snapshotted
 * @author John Hendrikx
 * @since 5.1.0
 */
@Internal
public interface Snapshotter<I, E> {

    /**
     * Asynchronously retrieves the snapshot for the given {@code identifier}, if one exists.
     * <p>
     * The returned {@link CompletableFuture} completes with the snapshot, or {@code null} if none exists
     * or the snapshot cannot be used. It may complete exceptionally if snapshot retrieval fails.
     *
     * @param identifier the identifier of the entity, cannot be {@code null}
     * @param context the current {@link ProcessingContext}, cannot be {@code null}
     * @return a {@link CompletableFuture} that completes with the entity's snapshot or {@code null}
     * @throws NullPointerException if {@code identifier} is {@code null}
     */
    CompletableFuture<Snapshot> load(I identifier, ProcessingContext context);

    /**
     * Stores the given entity as a snapshot asynchronously.
     *
     * @param identifier the identifier of the entity, cannot be {@code null}
     * @param entity the entity state, cannot be {@code null}
     * @param position the position in the event stream for this entity state, cannot be {@code null}
     * @param context the current {@link ProcessingContext}, cannot be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    void store(I identifier, E entity, Position position, ProcessingContext context);
}
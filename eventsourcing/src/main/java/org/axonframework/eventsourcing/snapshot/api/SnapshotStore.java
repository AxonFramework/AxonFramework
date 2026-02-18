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

import org.axonframework.messaging.core.MessageType;

import java.util.concurrent.CompletableFuture;

/**
 * Represents storage for snapshots of event-sourced entities.
 * <p>
 * A {@code SnapshotStore} is responsible for persisting and retrieving snapshots
 * to optimize the loading of entities by avoiding replaying the full event history.
 * <p>
 * Implementations are expected to be thread-safe and fully asynchronous, returning
 * {@link CompletableFuture} for all operations.
 *
 * @author John Hendrikx
 * @since 5.1.0
 */
public interface SnapshotStore {

    // TODO #4201 message type is a bad name here, we'll introduce VersionedType interface
    /**
     * Persists a snapshot of an entity.
     * <p>
     * This method is asynchronous and returns a {@link CompletableFuture} that
     * completes when the snapshot has been durably stored.
     *
     * @param type the type of the entity or message, identifies the snapshot category, cannot be {@code null}
     * @param identifier the unique identifier of the entity, cannot be {@code null}
     * @param snapshot the snapshot to persist, cannot be {@code null}
     * @return a {@link CompletableFuture} that completes when the snapshot has been stored
     * @throws NullPointerException if any argument is {@code null}
     */
    CompletableFuture<Void> store(MessageType type, Object identifier, Snapshot snapshot);

    /**
     * Loads the latest snapshot for a given entity.
     * <p>
     * This method is asynchronous and returns a {@link CompletableFuture} that
     * completes with the snapshot if one exists, or {@code null} if no snapshot
     * is available.
     *
     * @param type the type of the entity or message, identifies the snapshot category, cannot be {@code null}
     * @param identifier the unique identifier of the entity, cannot be {@code null}
     * @return a {@link CompletableFuture} containing the snapshot, or containing {@code null} if none exists
     * @throws NullPointerException if any argument is {@code null}
     */
    CompletableFuture<Snapshot> load(MessageType type, Object identifier);

}

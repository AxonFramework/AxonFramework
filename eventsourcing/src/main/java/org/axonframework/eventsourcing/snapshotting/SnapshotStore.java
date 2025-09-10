/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.snapshotting;

import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SnapshotStore {

    @Nonnull
    default CompletableFuture<Void> add(@Nonnull String identifier,
                                        long sequence,
                                        @Nonnull Snapshot snapshot) {
        return add(identifier, sequence, snapshot, true);
    }

    @Nonnull
    CompletableFuture<Void> add(@Nonnull String identifier,
                                long sequence,
                                @Nonnull Snapshot snapshot,
                                boolean prune);


    @Nonnull
    CompletableFuture<Void> delete(@Nonnull String identifier,
                                   long fromSequence,
                                   long toSequence);

    CompletableFuture<List<SnapshotEntry>> list(@Nonnull String identifier,
                                                long fromSequence,
                                                long toSequence);

    CompletableFuture<SnapshotEntry> getLast(@Nonnull String identifier);
}

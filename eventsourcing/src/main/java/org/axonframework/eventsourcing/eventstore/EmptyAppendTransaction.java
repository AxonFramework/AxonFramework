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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * Represents an empty append transaction. This transaction does nothing and always succeeds. It is used when there are
 * no events to persist.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public record EmptyAppendTransaction() implements EventStorageEngine.AppendTransaction<Void> {

    /**
     * The single instance of the {@code EmptyAppendTransaction}.
     */
    public static final EventStorageEngine.AppendTransaction<Void> INSTANCE = new EmptyAppendTransaction();

    @Override
    public CompletableFuture<Void> commit() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void rollback() {
        // No action needed
    }

    /**
     * Always provides the consistency marker {@link ConsistencyMarker#ORIGIN}.
     *
     * @param commitResult The result returned from the commit call.
     * @param context The current {@link ProcessingContext}, if any.
     * @return An empty always a completed future with the consistency marker {@link ConsistencyMarker#ORIGIN}.
     */
    @Override
    public CompletableFuture<ConsistencyMarker> afterCommit(@Nullable Void commitResult) {
        return CompletableFuture.completedFuture(ConsistencyMarker.ORIGIN);
    }
}

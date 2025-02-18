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

package org.axonframework.eventsourcing.eventstore;

import java.util.concurrent.CompletableFuture;

/**
 * Represents an empty append transaction. This transaction does nothing and always succeeds. It is used when there are
 * no events to persist.
 */
public record EmptyAppendTransaction() implements AsyncEventStorageEngine.AppendTransaction {

    public static final AsyncEventStorageEngine.AppendTransaction INSTANCE = new EmptyAppendTransaction();

    /**
     * Commits the empty append transaction. Always completes successfully with the provided consistency marker.
     *
     * @return always a completed future with the consistency marker {@link ConsistencyMarker#ORIGIN}
     */
    @Override
    public CompletableFuture<ConsistencyMarker> commit() {
        return CompletableFuture.completedFuture(ConsistencyMarker.ORIGIN);
    }

    /**
     * Rolls back the empty append transaction. This does nothing as the transaction has no effect.
     */
    @Override
    public void rollback() {
        // No action needed
    }
}

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

package org.axonframework.messaging.commandhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link CommandResult} that wraps a completable future providing the {@link Message} that represents the result.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class FutureCommandResult implements CommandResult {

    private final CompletableFuture<? extends Message> completableFuture;

    /**
     * Initializes the CommandResult based on the given {@code result} the completes when the result {@link Message}
     * becomes available
     *
     * @param result The completable future that provides the result message when available
     */
    public FutureCommandResult(@Nonnull CompletableFuture<? extends Message> result) {
        this.completableFuture = Objects.requireNonNull(result, "The result may not be null.");
    }

    @Override
    public CompletableFuture<? extends Message> getResultMessage() {
        return completableFuture;
    }
}

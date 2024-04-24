/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.commandhandling.gateway;

import org.axonframework.messaging.Message;

import java.util.concurrent.CompletableFuture;

/**
 * CommandResult that wraps a completable future providing the Message that represents the result
 */
public class FutureCommandResult implements CommandResult {

    private final CompletableFuture<? extends Message<?>> completableFuture;

    /**
     * Initializes the CommandResult based on the given {@code result} the completes when the result {@link Message}
     * becomes available
     *
     * @param result The completable future that provides the result message when available
     */
    public FutureCommandResult(CompletableFuture<? extends Message<?>> result) {
        this.completableFuture = result;
    }

    @Override
    public CompletableFuture<? extends Message<?>> getResultMessage() {
        return completableFuture;
    }
}

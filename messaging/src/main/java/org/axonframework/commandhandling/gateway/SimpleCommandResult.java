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

public class SimpleCommandResult implements CommandResult {

    private final CompletableFuture<? extends Message<?>> completableFuture;

    public SimpleCommandResult(CompletableFuture<? extends Message<?>> completableFuture) {
        this.completableFuture = completableFuture;
    }

    @Override
    public CompletableFuture<? extends Message<?>> getResultMessage() {
        return completableFuture;
    }
}

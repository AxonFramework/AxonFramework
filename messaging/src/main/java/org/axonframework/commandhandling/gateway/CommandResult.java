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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface CommandResult {

    default <R> CompletableFuture<R> resultAs(Class<R> type) {
        return getResultMessage().thenApply(Message::getPayload)
                                 .thenApply(type::cast);
    }

    CompletableFuture<? extends Message<?>> getResultMessage();

    default CommandResult onSuccess(Consumer<Message<?>> resultHandler) {
        getResultMessage().whenComplete((r, e) -> {
            if (e == null) {
                resultHandler.accept(r);
            }
        });
        return this;
    }

    default <R> CommandResult onSuccess(Class<R> returnType, BiConsumer<R, Message<?>> handler) {
        getResultMessage().whenComplete((r, e) -> {
            if (e == null) {
                handler.accept(returnType.cast(r.getPayload()), r);
            }
        });
        return this;
    }

    default <R> CommandResult onSuccess(Class<R> returnType, Consumer<R> handler) {
        return onSuccess(returnType, (result, message) -> handler.accept(result));
    }

    default CommandResult onError(Consumer<Throwable> errorHandler) {
        getResultMessage().whenComplete((r, e) -> {
            if (e != null) {
                errorHandler.accept(e);
            }
        });
        return this;
    }
}

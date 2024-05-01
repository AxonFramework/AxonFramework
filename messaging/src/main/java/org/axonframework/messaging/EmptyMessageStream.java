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

package org.axonframework.messaging;

import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

class EmptyMessageStream<T extends Message<?>> implements MessageStream<T> {

    @SuppressWarnings("rawtypes")
    private static final EmptyMessageStream INSTANCE = new EmptyMessageStream();

    private EmptyMessageStream() {
    }

    public static <T extends Message<?>> EmptyMessageStream<T> instance() {
        //noinspection unchecked
        return INSTANCE;
    }

    @Override
    public CompletableFuture<T> asCompletableFuture() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Flux<T> asFlux() {
        return Flux.empty();
    }

    @Override
    public <R extends Message<?>> MessageStream<R> map(Function<T, R> mapper) {
        //noinspection unchecked
        return (MessageStream<R>) this;
    }

    @Override
    public MessageStream<T> onNextItem(Consumer<T> handler) {
        return this;
    }

    @Override
    public MessageStream<T> whenComplete(Runnable completeHandler) {
        try {
            completeHandler.run();
            return this;
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    @Override
    public MessageStream<T> onErrorContinue(Function<Throwable, MessageStream<T>> onError) {
        return this;
    }
}

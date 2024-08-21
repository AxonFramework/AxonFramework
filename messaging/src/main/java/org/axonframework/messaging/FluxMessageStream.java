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

import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation using a {@link Flux} as the {@link Message} source.
 *
 * @param <M> The type of {@link Message} carried in this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class FluxMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final Flux<M> source;

    /**
     * Constructs a {@link MessageStream} using the given {@code source} to provide the {@link Message Messages}.
     *
     * @param source The {@link Flux} sourcing the {@link Message Messages} for this {@link MessageStream}.
     */
    FluxMessageStream(@NotNull Flux<M> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<M> asCompletableFuture() {
        return source.next().toFuture();
    }

    @Override
    public Flux<M> asFlux() {
        return source;
    }

    @Override
    public <R extends Message<?>> MessageStream<R> map(@NotNull Function<M, R> mapper) {
        return new FluxMessageStream<>(source.map(mapper));
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity, @NotNull BiFunction<R, M, R> accumulator) {
        return source.reduce(identity, accumulator).toFuture();
    }

    @Override
    public MessageStream<M> onErrorContinue(@NotNull Function<Throwable, MessageStream<M>> onError) {
        return new FluxMessageStream<>(source.onErrorResume(e -> onError.apply(e).asFlux()));
    }

    @Override
    public MessageStream<M> whenComplete(@NotNull Runnable completeHandler) {
        return new FluxMessageStream<>(source.doOnComplete(completeHandler));
    }
}

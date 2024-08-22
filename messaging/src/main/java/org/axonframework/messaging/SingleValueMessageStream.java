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
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation using a single {@link Message} or {@link CompletableFuture} completing to a
 * {@code Message} as the source.
 *
 * @param <M> The type of {@link Message} carried in this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SingleValueMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final CompletableFuture<M> source;

    /**
     * Constructs a {@link MessageStream} wrapping the given {@code message} into a
     * {@link CompletableFuture#completedFuture(Object) completed CompletableFuture} as the single value in this
     * stream.
     *
     * @param message The {@link Message} of type {@code M} which is the singular value contained in this
     *                {@link MessageStream}.
     */
    SingleValueMessageStream(M message) {
        this(CompletableFuture.completedFuture(message));
    }

    /**
     * Constructs a {@link MessageStream} with the given {@code source} as the provider of the single {@link Message} in
     * this stream.
     *
     * @param source The {@link CompletableFuture} resulting in the singular {@link Message} contained in this
     *               {@link MessageStream}.
     */
    SingleValueMessageStream(@NotNull CompletableFuture<M> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<M> asCompletableFuture() {
        return source;
    }

    @Override
    public Flux<M> asFlux() {
        return Flux.from(Mono.fromFuture(source));
    }

    @Override
    public <R extends Message<?>> MessageStream<R> map(Function<M, R> mapper) {
        return new SingleValueMessageStream<>(source.thenApply(mapper));
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity, @NotNull BiFunction<R, M, R> accumulator) {
        return source.thenApply(m -> accumulator.apply(identity, m));
    }
}

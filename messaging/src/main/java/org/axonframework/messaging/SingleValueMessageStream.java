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
 * A {@link MessageStream} implementation using a single entry of type {@code E} or {@link CompletableFuture} completing
 * to an entry of type {@code E} as the source.
 *
 * @param <E> The type of entry carried in this {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SingleValueMessageStream<E> implements MessageStream<E> {

    private final CompletableFuture<E> source;

    /**
     * Constructs a {@link MessageStream stream} wrapping the given {@code entry} into a
     * {@link CompletableFuture#completedFuture(Object) completed CompletableFuture} as the single value in this
     * stream.
     *
     * @param entry The entry of type {@code E} which is the singular value contained in this
     *              {@link MessageStream stream}.
     */
    SingleValueMessageStream(E entry) {
        this(CompletableFuture.completedFuture(entry));
    }

    /**
     * Constructs a {@link MessageStream stream} with the given {@code source} as the provider of the single entry of
     * type {@code E} in this stream.
     *
     * @param source The {@link CompletableFuture} resulting in the singular entry of type {@code E} contained in this
     *               {@link MessageStream stream}.
     */
    SingleValueMessageStream(@NotNull CompletableFuture<E> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<E> asCompletableFuture() {
        return source;
    }

    @Override
    public Flux<E> asFlux() {
        return Flux.from(Mono.fromFuture(source));
    }

    @Override
    public <R> MessageStream<R> map(Function<E, R> mapper) {
        return new SingleValueMessageStream<>(source.thenApply(mapper));
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, E, R> accumulator) {
        return source.thenApply(message -> accumulator.apply(identity, message));
    }
}

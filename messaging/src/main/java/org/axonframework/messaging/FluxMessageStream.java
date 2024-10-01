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
 * A {@link MessageStream} implementation using a {@link Flux} as the source.
 *
 * @param <E> The type of entry carried in this {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class FluxMessageStream<E> implements MessageStream<E> {

    private final Flux<E> source;

    /**
     * Constructs a {@link MessageStream stream} using the given {@code source} to provide the entries of type
     * {@code E}.
     *
     * @param source The {@link Flux} providing the entries of type {@code E} for this {@link MessageStream stream}.
     */
    FluxMessageStream(@NotNull Flux<E> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<E> asCompletableFuture() {
        return source.singleOrEmpty()
                     .toFuture();
    }

    @Override
    public Flux<E> asFlux() {
        return source;
    }

    @Override
    public <R> MessageStream<R> map(@NotNull Function<E, R> mapper) {
        return new FluxMessageStream<>(source.map(mapper));
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, E, R> accumulator) {
        return source.reduce(identity, accumulator)
                     .toFuture();
    }

    @Override
    public MessageStream<E> onErrorContinue(@NotNull Function<Throwable, MessageStream<E>> onError) {
        return new FluxMessageStream<>(source.onErrorResume(
                exception -> onError.apply(exception)
                                    .asFlux()
        ));
    }

    @Override
    public MessageStream<E> whenComplete(@NotNull Runnable completeHandler) {
        return new FluxMessageStream<>(source.doOnComplete(completeHandler));
    }
}

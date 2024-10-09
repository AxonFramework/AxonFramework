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

import jakarta.annotation.Nonnull;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation using a {@link Flux} as the source for {@link MessageEntry entries}.
 *
 * @param <M> The type of {@link Message} contained in the {@link MessageEntry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class FluxMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final Flux<MessageEntry<M>> source;

    /**
     * Constructs a {@link MessageStream stream} using the given {@code source} to provide the
     * {@link MessageEntry entries}.
     *
     * @param source The {@link Flux} providing the {@link MessageEntry entries} for this {@link MessageStream stream}.
     */
    FluxMessageStream(@Nonnull Flux<MessageEntry<M>> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<MessageEntry<M>> asCompletableFuture() {
        return source.singleOrEmpty().toFuture();
    }

    @Override
    public Flux<MessageEntry<M>> asFlux() {
        return source;
    }

    @Override
    public <RM extends Message<?>> MessageStream<RM> map(@Nonnull Function<MessageEntry<M>, MessageEntry<RM>> mapper) {
        return new FluxMessageStream<>(source.map(mapper));
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, MessageEntry<M>, R> accumulator) {
        return source.reduce(identity, accumulator).toFuture();
    }

    @Override
    public MessageStream<M> onErrorContinue(@Nonnull Function<Throwable, MessageStream<M>> onError) {
        return new FluxMessageStream<>(source.onErrorResume(exception -> onError.apply(exception).asFlux()));
    }

    @Override
    public MessageStream<M> whenComplete(@Nonnull Runnable completeHandler) {
        return new FluxMessageStream<>(source.doOnComplete(completeHandler));
    }
}

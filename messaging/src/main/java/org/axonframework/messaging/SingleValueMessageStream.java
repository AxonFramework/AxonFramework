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
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation using a single {@link MessageEntry entry} or {@link CompletableFuture}
 * completing to an entry as the source.
 *
 * @param <M> The type of {@link Message} contained in the {@link MessageEntry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SingleValueMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final CompletableFuture<MessageEntry<M>> source;

    /**
     * Constructs a {@link MessageStream stream} wrapping the given {@link MessageEntry entry} into a
     * {@link CompletableFuture#completedFuture(Object) completed CompletableFuture} as the single entry in this
     * stream.
     *
     * @param entry The {@link MessageEntry entry} which is the singular value contained in this
     *              {@link MessageStream stream}.
     */
    SingleValueMessageStream(@Nullable MessageEntry<M> entry) {
        this(CompletableFuture.completedFuture(entry));
    }

    /**
     * Constructs a {@link MessageStream stream} with the given {@code source} as the provider of the single
     * {@link MessageEntry entry} in this stream.
     *
     * @param source The {@link CompletableFuture} resulting in the singular {@link MessageEntry entry} contained in
     *               this {@link MessageStream stream}.
     */
    SingleValueMessageStream(@Nonnull CompletableFuture<MessageEntry<M>> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<MessageEntry<M>> asCompletableFuture() {
        return source;
    }

    @Override
    public Flux<MessageEntry<M>> asFlux() {
        return Flux.from(Mono.fromFuture(source));
    }

    @Override
    public <RM extends Message<?>> MessageStream<RM> map(@Nonnull Function<MessageEntry<M>, MessageEntry<RM>> mapper) {
        return new SingleValueMessageStream<>(source.thenApply(mapper));
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, MessageEntry<M>, R> accumulator) {
        return source.thenApply(message -> accumulator.apply(identity, message));
    }
}

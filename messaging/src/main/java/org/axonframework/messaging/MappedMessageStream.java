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
 * Implementation of the {@link MessageStream} that maps the entries of type {@code E} to type {@code RE}.
 *
 * @param <E>  The type of entry carried in this {@link MessageStream stream}.
 * @param <RE> The type of entry carried as output to this stream as a result of the provided mapper operation.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class MappedMessageStream<E, RE> implements MessageStream<RE> {

    private final MessageStream<E> delegate;
    private final Function<E, RE> mapper;

    /**
     * Construct a {@link MessageStream stream} mapping the entries of type {@code E} of the given {@code delegate}
     * {@code MessageStream} to type {@code RE}.
     *
     * @param delegate The {@link MessageStream stream} who's entries of type {@code E} are mapped with the given
     *                 {@code mapper}.
     * @param mapper   The {@link Function} mapping entries of type {@code E} to {@code RE}.
     */
    MappedMessageStream(@Nonnull MessageStream<E> delegate,
                        @Nonnull Function<E, RE> mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public CompletableFuture<RE> asCompletableFuture() {
        // CompletableFuture doesn't support empty completions, so null is used as placeholder
        return delegate.asCompletableFuture()
                       .thenApply(message -> message == null ? null : mapper.apply(message));
    }

    @Override
    public Flux<RE> asFlux() {
        return delegate.asFlux()
                       .map(mapper);
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, RE, R> accumulator) {
        return delegate.reduce(
                identity,
                (base, message) -> accumulator.apply(base, mapper.apply(message))
        );
    }
}

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
 * Implementation of the {@link MessageStream} that maps the {@link Entry entries}.
 *
 * @param <M>  The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @param <RM> The type of {@link Message} contained in the {@link Entry} as a result of mapping.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class MappedMessageStream<M extends Message<?>, RM extends Message<?>> implements MessageStream<RM> {

    private final MessageStream<M> delegate;
    private final Function<Entry<M>, Entry<RM>> mapper;

    /**
     * Construct a {@link MessageStream stream} mapping the {@link Entry entries} of the given
     * {@code delegate MessageStream} to entries containing {@link Message Messages} of type {@code RM}.
     *
     * @param delegate The {@link MessageStream stream} who's {@link Entry entries} are mapped with the given
     *                 {@code mapper}.
     * @param mapper   The {@link Function} mapping {@link Entry entries}.
     */
    MappedMessageStream(@Nonnull MessageStream<M> delegate,
                        @Nonnull Function<Entry<M>, Entry<RM>> mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public CompletableFuture<Entry<RM>> firstAsCompletableFuture() {
        // CompletableFuture doesn't support empty completions, so null is used as placeholder
        return delegate.firstAsCompletableFuture()
                       .thenApply(entry -> entry == null ? null : mapper.apply(entry));
    }

    @Override
    public Flux<Entry<RM>> asFlux() {
        return delegate.asFlux()
                       .map(mapper);
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, Entry<RM>, R> accumulator) {
        return delegate.reduce(
                identity,
                (base, message) -> accumulator.apply(base, mapper.apply(message))
        );
    }
}

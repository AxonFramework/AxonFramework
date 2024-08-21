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
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A {@link MessageStream} implementation using a {@link Stream} as the {@link Message} source.
 *
 * @param <M> The type of {@link Message} carried in this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class StreamMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final Stream<M> source;

    /**
     * Constructs a {@link MessageStream} using the given {@code source} to provide the {@link Message Messages}.
     *
     * @param source The {@link Stream} sourcing the {@link Message Messages} for this {@link MessageStream}.
     */
    StreamMessageStream(@NotNull Stream<M> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<M> asCompletableFuture() {
        return CompletableFuture.completedFuture(source.findFirst().orElse(null));
    }

    @Override
    public Flux<M> asFlux() {
        return Flux.fromStream(source);
    }

    @Override
    public <R extends Message<?>> MessageStream<R> map(Function<M, R> mapper) {
        return new StreamMessageStream<>(source.map(mapper));
    }
}

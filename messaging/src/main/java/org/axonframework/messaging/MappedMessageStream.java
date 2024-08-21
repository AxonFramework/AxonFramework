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

/**
 * Implementation of the {@link MessageStream} that maps the {@link Message Messages} from type {@code M} to type
 * {@code R}.
 *
 * @param <R> The type of {@link Message} carried as output to this stream.
 * @param <M> The type of {@link Message} carried as input to this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class MappedMessageStream<R extends Message<?>, M extends Message<?>> implements MessageStream<M> {

    private final MessageStream<R> delegate;
    private final Function<R, M> mapper;

    /**
     * Construct a {@link MappedMessageStream} mapping the {@link Message Messages} of the given {@code delegate}
     * {@link MessageStream} to type {@code M}.
     *
     * @param delegate The {@link MessageStream} from which its {@link Message Messages} are mapped with the given
     *                 {@code mapper}.
     * @param mapper   The {@link Function} mapping {@link Message Messages} of type {@code R} to {@code M}.
     */
    MappedMessageStream(@NotNull MessageStream<R> delegate,
                        @NotNull Function<R, M> mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public CompletableFuture<M> asCompletableFuture() {
        // CompletableFuture doesn't support empty completions, so null is used as placeholder
        return delegate.asCompletableFuture()
                       .thenApply(c -> c == null ? null : mapper.apply(c));
    }

    @Override
    public Flux<M> asFlux() {
        return delegate.asFlux().map(mapper);
    }
}

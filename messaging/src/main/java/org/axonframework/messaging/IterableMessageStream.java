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
import org.axonframework.common.FutureUtils;
import reactor.core.publisher.Flux;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.StreamSupport;

/**
 * A {@link MessageStream} implementation using an {@link Iterable} as the source.
 *
 * @param <E> The type of entry carried in this {@link MessageStream stream}.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class IterableMessageStream<E> implements MessageStream<E> {

    private static final boolean NOT_PARALLEL = false;

    private final Iterable<E> source;

    /**
     * Constructs a {@link MessageStream stream} using the given {@code source} to provide the entries of type
     * {@code E}.
     *
     * @param source The {@link Iterable} providing the entries of type {@code E} for this
     *               {@link MessageStream stream}.
     */
    IterableMessageStream(@NotNull Iterable<E> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<E> asCompletableFuture() {
        Iterator<E> iterator = source.iterator();
        return iterator.hasNext()
                ? CompletableFuture.completedFuture(iterator.next())
                : FutureUtils.emptyCompletedFuture();
    }

    @Override
    public Flux<E> asFlux() {
        return Flux.fromIterable(source);
    }

    @Override
    public <R> MessageStream<R> map(Function<E, R> mapper) {
        return new IterableMessageStream<>(
                StreamSupport.stream(source.spliterator(), NOT_PARALLEL)
                             .map(mapper)
                             .toList()
        );
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, E, R> accumulator) {
        return CompletableFuture.completedFuture(
                StreamSupport.stream(source.spliterator(), NOT_PARALLEL)
                             .reduce(identity, accumulator, (thisResult, thatResult) -> {
                                 throw new UnsupportedOperationException(
                                         "Cannot combine reduce results as parallelized reduce is not supported."
                                 );
                             })
        );
    }
}

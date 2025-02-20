/*
 * Copyright (c) 2010-2025. Axon Framework
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation using a single {@link Entry entry} or {@link CompletableFuture} completing to
 * an entry as the source.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SingleValueMessageStream<M extends Message<?>> implements MessageStream.Single<M> {

    private final CompletableFuture<Entry<M>> source;
    private final AtomicBoolean read = new AtomicBoolean(false);

    /**
     * Constructs a {@link MessageStream stream} wrapping the given {@link Entry entry} into a
     * {@link CompletableFuture#completedFuture(Object) completed CompletableFuture} as the single entry in this
     * stream.
     *
     * @param entry The {@link Entry entry} which is the singular value contained in this {@link MessageStream stream}.
     */
    SingleValueMessageStream(@Nullable Entry<M> entry) {
        this(CompletableFuture.completedFuture(entry));
    }

    /**
     * Constructs a {@link MessageStream stream} with the given {@code source} as the provider of the single
     * {@link Entry entry} in this stream.
     *
     * @param source The {@link CompletableFuture} resulting in the singular {@link Entry entry} contained in this
     *               {@link MessageStream stream}.
     */
    SingleValueMessageStream(@Nonnull CompletableFuture<Entry<M>> source) {
        this.source = source;
    }

    @Override
    public CompletableFuture<Entry<M>> asCompletableFuture() {
        return source;
    }

    @Override
    public Flux<Entry<M>> asFlux() {
        return Flux.from(asMono());
    }

    @Override
    public Mono<Entry<M>> asMono() {
        return Mono.fromFuture(source);
    }

    @Override
    public Optional<Entry<M>> next() {
        if (source.isDone() && !source.isCompletedExceptionally()) {
            Entry<M> current = source.getNow(null);
            if (read.compareAndSet(false, true)) {
                return Optional.of(current);
            }
        }
        return Optional.empty();
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        if (source.isDone()) {
            callback.run();
        } else {
            source.whenComplete((entry, throwable) -> callback.run());
        }
    }

    @Override
    public Optional<Throwable> error() {
        return source.isCompletedExceptionally() ? Optional.of(source.exceptionNow()) : Optional.empty();
    }

    @Override
    public boolean isCompleted() {
        return source.isCompletedExceptionally() || read.get();
    }

    @Override
    public boolean hasNextAvailable() {
        return source.isDone() && !source.isCompletedExceptionally() && !read.get();
    }

    @Override
    public void close() {
        if (!source.isDone()) {
            source.cancel(false);
        }
    }

    @Override
    public <RM extends Message<?>> Single<RM> map(@Nonnull Function<Entry<M>, Entry<RM>> mapper) {
        return new SingleValueMessageStream<>(source.thenApply(mapper));
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, Entry<M>, R> accumulator) {
        return source.thenApply(message -> accumulator.apply(identity, message));
    }
}

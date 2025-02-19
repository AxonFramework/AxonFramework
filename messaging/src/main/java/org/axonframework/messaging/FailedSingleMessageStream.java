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
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class FailedSingleMessageStream<M extends Message<?>> implements MessageStream.Single<M> {

    private final FailedMessageStream<M> delegate;

    public FailedSingleMessageStream(Throwable error) {
        this.delegate = new FailedMessageStream<>(error);
    }

    @Override
    public CompletableFuture<Entry<M>> firstAsCompletableFuture() {
        return delegate.firstAsCompletableFuture();
    }

    @Override
    public Flux<Entry<M>> asFlux() {
        return delegate.asFlux();
    }

    @Override
    public Optional<Entry<M>> next() {
        return delegate.next();
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        delegate.onAvailable(callback);
    }

    @Override
    public Optional<Throwable> error() {
        return delegate.error();
    }

    @Override
    public boolean isCompleted() {
        return delegate.isCompleted();
    }

    @Override
    public boolean hasNextAvailable() {
        return delegate.hasNextAvailable();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public <RM extends Message<?>> MessageStream<RM> map(@Nonnull Function<Entry<M>, Entry<RM>> mapper) {
        return delegate.map(mapper);
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, Entry<M>, R> accumulator) {
        return delegate.reduce(identity, accumulator);
    }

    @Override
    public MessageStream<M> whenComplete(@Nonnull Runnable completeHandler) {
        return delegate.whenComplete(completeHandler);
    }

    @Override
    public MessageStream<M> concatWith(@Nonnull MessageStream<M> other) {
        return delegate.concatWith(other);
    }
}

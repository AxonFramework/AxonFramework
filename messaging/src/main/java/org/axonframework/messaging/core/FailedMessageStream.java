/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation that completes exceptionally through the given {@code error}.
 *
 * @param <M> The type of {@link Message} for the empty {@link Entry} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class FailedMessageStream<M extends Message> extends AbstractMessageStream<M> implements MessageStream.Empty<M> {

    /**
     * Constructs a {@link MessageStream stream} that will complete exceptionally with the given {@code error}.
     *
     * @param error The {@link Throwable} that caused this {@link MessageStream stream} to complete exceptionally.
     */
    FailedMessageStream(@Nonnull Throwable error) {
        completeExceptionally(error);
    }

    @Override
    public CompletableFuture<Entry<M>> asCompletableFuture() {
        //noinspection OptionalGetWithoutIsPresent
        return CompletableFuture.failedFuture(error().get());
    }

    @Override
    public Optional<Entry<M>> next() {
        return Optional.empty();
    }

    @Override
    public boolean isCompleted() {
        return true;
    }

    @Override
    public boolean hasNextAvailable() {
        return false;
    }

    @Override
    public void close() {
    }

    @Override
    public <RM extends Message> Empty<RM> map(@Nonnull Function<Entry<M>, Entry<RM>> mapper) {
        //noinspection unchecked
        return (FailedMessageStream<RM>) this;
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, Entry<M>, R> accumulator) {
        //noinspection OptionalGetWithoutIsPresent
        return CompletableFuture.failedFuture(error().get());
    }

    @Override
    public Empty<M> onComplete(@Nonnull Runnable completeHandler) {
        return this;
    }

    @Override
    public MessageStream<M> concatWith(@Nonnull MessageStream<M> other) {
        return this;
    }

    @Override
    public Optional<Entry<M>> peek() {
        return Optional.empty();
    }
}

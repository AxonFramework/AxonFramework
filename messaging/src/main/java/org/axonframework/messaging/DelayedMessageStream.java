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
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

/**
 * An implementation of the {@link MessageStream} that wraps a stream that will become available asynchronously.
 *
 * @param <M> The type of {@link Message} carried in this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DelayedMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final CompletableFuture<MessageStream<M>> delegate;

    private DelayedMessageStream(@NotNull CompletableFuture<MessageStream<M>> delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a {@link MessageStream} that delays actions to its {@code delegate} when it becomes available.
     * <p>
     * If the given {@code delegate} has already {@link CompletableFuture#isDone() completed}, it returns the
     * {@code MessageStream} immediately from it. Otherwise, it returns a {@link DelayedMessageStream} instance wrapping
     * the given {@code delegate}.
     *
     * @param delegate A {@link CompletableFuture} providing access to the {@link MessageStream} to delegate to when it
     *                 becomes available.
     * @param <M>      The type of {@link Message} carried in this stream.
     * @return A {@link MessageStream} that delegates all actions to the {@code delegate} when it becomes available.
     */
    public static <M extends Message<?>> MessageStream<M> create(CompletableFuture<MessageStream<M>> delegate) {
        if (delegate.isDone()) {
            try {
                return delegate.get();
            } catch (InterruptedException e) {
                return new DelayedMessageStream<>(delegate);
            } catch (ExecutionException e) {
                return MessageStream.failed(e.getCause());
            }
        }
        return new DelayedMessageStream<>(delegate.exceptionallyCompose(CompletableFuture::failedFuture));
    }

    @Override
    public CompletableFuture<M> asCompletableFuture() {
        return delegate.thenCompose(MessageStream::asCompletableFuture);
    }

    @Override
    public Flux<M> asFlux() {
        return Mono.fromFuture(delegate)
                   .flatMapMany(MessageStream::asFlux);
    }

    @Override
    public <R> CompletableFuture<R> reduce(@NotNull R identity,
                                           @NotNull BiFunction<R, M, R> accumulator) {
        return delegate.thenCompose(delegateStream -> delegateStream.reduce(identity, accumulator));
    }
}

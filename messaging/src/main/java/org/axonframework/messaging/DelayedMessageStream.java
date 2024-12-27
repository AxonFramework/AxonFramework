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
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

/**
 * An implementation of the {@link MessageStream} that wraps a stream that will become available asynchronously.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DelayedMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final CompletableFuture<MessageStream<M>> delegate;

    private DelayedMessageStream(@Nonnull CompletableFuture<MessageStream<M>> delegate) {
        this.delegate = delegate;
    }
    /**
     * Creates a {@link MessageStream stream} that delays actions to its {@code delegate} when it becomes available.
     * <p>
     * If the given {@code delegate} has already {@link CompletableFuture#isDone() completed}, it returns the
     * {@code MessageStream} immediately from it. Otherwise, it returns a DelayedMessageStream instance wrapping
     * the given {@code delegate}.
     *
     * @param delegate A {@link CompletableFuture} providing access to the {@link MessageStream stream} to delegate to
     *                 when it becomes available.
     * @param <M>      The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A {@link MessageStream stream} that delegates all actions to the {@code delegate} when it becomes
     * available.
     */
    public static <M extends Message<?>> MessageStream<M> create(
            @Nonnull CompletableFuture<MessageStream<M>> delegate) {
        CompletableFuture<MessageStream<M>> safeDelegate = delegate
                .exceptionallyCompose(CompletableFuture::failedFuture)
                .thenApply(ms -> Objects.requireNonNullElse(ms, EmptyMessageStream.instance()));
        if (safeDelegate.isDone()) {
            try {
                return delegate.get();
            } catch (InterruptedException e) {
                return new DelayedMessageStream<>(safeDelegate);
            } catch (ExecutionException e) {
                return MessageStream.failed(e.getCause());
            }
        }
        return new DelayedMessageStream<>(safeDelegate);
    }

    @Override
    public CompletableFuture<Entry<M>> firstAsCompletableFuture() {
        return delegate.thenCompose(MessageStream::firstAsCompletableFuture);
    }

    @Override
    public Flux<Entry<M>> asFlux() {
        return Mono.fromFuture(delegate).flatMapMany(MessageStream::asFlux);
    }

    @Override
    public Optional<Entry<M>> next() {
        if (delegate.isDone()) {
            return delegate.getNow(null).next();
        }
        return Optional.empty();
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        delegate.whenComplete((r, e) -> {
            if (r != null) {
                r.onAvailable(callback);
            } else {
                callback.run();
            }
        });
    }

    @Override
    public Optional<Throwable> error() {
        if (delegate.isDone()) {
            if (delegate.isCompletedExceptionally()) {
                return Optional.of(delegate.exceptionNow());
            } else {
                return delegate.getNow(null).error();
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isCompleted() {
        return delegate.isDone() && delegate.getNow(null).isCompleted();
    }

    @Override
    public boolean hasNextAvailable() {
        return delegate.isDone() && (delegate.isCompletedExceptionally() || delegate.getNow(null).isCompleted());
    }

    @Override
    public void close() {
        if (!delegate.isDone()) {
            delegate.cancel(false);
        } else {
            delegate.getNow(null).close();
        }
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity, @Nonnull BiFunction<R, Entry<M>, R> accumulator) {
        return delegate.thenCompose(delegateStream -> delegateStream.reduce(identity, accumulator));
    }
}

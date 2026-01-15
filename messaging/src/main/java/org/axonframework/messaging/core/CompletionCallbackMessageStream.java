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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * Implementation of the {@link MessageStream} that invokes the given {@code completeHandler} once the
 * {@code delegate MessageStream} completes.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class CompletionCallbackMessageStream<M extends Message> extends DelegatingMessageStream<M, M> {

    private final MessageStream<M> delegate;
    private final Runnable completeHandler;
    private final AtomicBoolean invoked = new AtomicBoolean(false);

    /**
     * Construct a {@link MessageStream stream} invoking the given {@code completeHandler} when the given
     * {@code delegate} completes.
     *
     * @param delegate        The {@link MessageStream stream} that once completed results in the invocation of the
     *                        given {@code completeHandler}.
     * @param completeHandler The {@link Runnable} to invoke when the given {@code delegate} completes.
     */
    CompletionCallbackMessageStream(@Nonnull MessageStream<M> delegate,
                                    @Nonnull Runnable completeHandler) {
        super(delegate);
        this.delegate = delegate;
        this.completeHandler = completeHandler;
        delegate.setCallback(this::invokeCompletionHandlerIfCompleted);
    }

    @Override
    public Optional<Entry<M>> next() {
        Optional<Entry<M>> next = delegate.next();
        if (next.isEmpty()) {
            invokeCompletionHandlerIfCompleted();
        }
        return next;
    }

    @Override
    public Optional<Entry<M>> peek() {
        Optional<Entry<M>> peek = delegate.peek();
        if (peek.isEmpty()) {
            invokeCompletionHandlerIfCompleted();
        }
        return peek;
    }

    private void invokeCompletionHandlerIfCompleted() {
        if (delegate.isCompleted() && delegate.error().isEmpty() && !invoked.getAndSet(true)) {
            completeHandler.run();
        }
    }

    @Override
    public void setCallback(@Nonnull Runnable callback) {
        delegate.setCallback(() -> {
            callback.run();
            invokeCompletionHandlerIfCompleted();
        });
    }

    @Override
    public Optional<Throwable> error() {
        invokeCompletionHandlerIfCompleted();
        return delegate.error();
    }

    @Override
    public boolean hasNextAvailable() {
        boolean b = delegate.hasNextAvailable();
        if (!b && delegate().isCompleted()) {
            invokeCompletionHandlerIfCompleted();
        }
        return b;
    }

    @Override
    public <R> CompletableFuture<R> reduce(@Nonnull R identity,
                                           @Nonnull BiFunction<R, Entry<M>, R> accumulator) {
        return delegate.reduce(identity, accumulator)
                       .whenComplete((result, exception) -> {
                           if (exception == null) {
                               completeHandler.run();
                           }
                       });
    }

    /**
     * A {@link CompletionCallbackMessageStream} implementation completing on only the first
     * {@link MessageStream.Entry} of this stream.
     *
     * @param <M> The type of {@link Message} contained in the {@link Entry} of this stream.
     */
    static class Single<M extends Message> extends CompletionCallbackMessageStream<M>
            implements MessageStream.Single<M> {

        /**
         * Construct a {@link MessageStream stream} invoking the given {@code completeHandler} when the given
         * {@code delegate} completes.
         *
         * @param delegate        The {@link MessageStream stream} that once completed results in the invocation of the
         *                        given {@code completeHandler}.
         * @param completeHandler The {@link Runnable} to invoke when the given {@code delegate} completes.
         */
        Single(@Nonnull MessageStream.Single<M> delegate, @Nonnull Runnable completeHandler) {
            super(delegate, completeHandler);
        }
    }

    /**
     * A {@link CompletionCallbackMessageStream} implementation completing on no
     * {@link MessageStream.Entry} of this stream.
     *
     * @param <M> The type of {@link Message} for the empty {@link Entry} of this stream.
     */
    static class Empty<M extends Message> extends CompletionCallbackMessageStream<M>
            implements MessageStream.Empty<M> {

        /**
         * Construct a {@link MessageStream stream} invoking the given {@code completeHandler} when the given
         * {@code delegate} completes.
         *
         * @param delegate        The {@link MessageStream stream} that once completed results in the invocation of the
         *                        given {@code completeHandler}.
         * @param completeHandler The {@link Runnable} to invoke when the given {@code delegate} completes.
         */
        Empty(@Nonnull MessageStream.Empty<M> delegate, @Nonnull Runnable completeHandler) {
            super(delegate, completeHandler);
        }
    }
}

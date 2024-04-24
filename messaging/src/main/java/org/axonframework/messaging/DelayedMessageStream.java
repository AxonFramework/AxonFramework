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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * MessageStream implementation that wraps a stream that becomes available asynchronously.
 *
 * @param <T> The type of message carried in this Message Stream
 */
public class DelayedMessageStream<T extends Message<?>> implements MessageStream<T> {

    private final CompletableFuture<MessageStream<T>> delegate;

    private DelayedMessageStream(CompletableFuture<MessageStream<T>> delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a MessageStream that delays actions to its delegate when it becomes available.
     * <p>
     * If the given {@code delegate} has already completed, it returns the MessageStream immediately, otherwise it
     * returns in instance of DelayedMessageStream that
     *
     * @param delegate A CompletableFuture providing access to the MessageStream to delegate to when it becomes
     *                 available
     * @param <T>      The type of Message carried by this stream
     * @return A MessageStream that delegates all actions to the delegate when it becomes available
     */
    public static <T extends Message<?>> MessageStream<T> create(CompletableFuture<MessageStream<T>> delegate) {
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
    public CompletableFuture<T> asCompletableFuture() {
        return delegate.thenCompose(MessageStream::asCompletableFuture);
    }

    @Override
    public Flux<T> asFlux() {
        return Mono.fromFuture(delegate).flatMapMany(MessageStream::asFlux);
    }
}

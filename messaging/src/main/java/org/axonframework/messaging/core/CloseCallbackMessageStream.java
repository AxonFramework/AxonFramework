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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MessageStream implementation that invokes the given {@code closeHandler} once the stream is closed. A stream is
 * considered closed when a consumer explicitly calls {@link #close()} or when the stream is completed.
 * <p>
 * Note that when close is called on the delegate, or when the client does not attempt to consume this stream, the
 * close handler may never be invoked, even though the stream is completed.
 *
 * @param <M> The type of Message handled by this MessageStream.
 */
public class CloseCallbackMessageStream<M extends Message> extends DelegatingMessageStream<M, M> {

    private final Runnable closeHandler;
    private final AtomicBoolean invoked = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates an instance of the CloseCallbackMessageStream, calling the given {@code closeHandler} once this
     * stream is closed, or the given {@code delegate} completes.
     *
     * @param delegate     The MessageStream to wrap with the close handler invocation logic
     * @param closeHandler The handler to invoke when the stream is closed or completed
     */
    public CloseCallbackMessageStream(@Nonnull MessageStream<M> delegate, @Nonnull Runnable closeHandler) {
        super(delegate);
        this.closeHandler = Objects.requireNonNull(closeHandler, "Close handler may not be null.");
    }

    /**
     * Creates an instance of the CloseCallbackMessageStream, calling the given {@code closeHandler} once this
     * stream is closed, or the given {@code delegate} completes.
     *
     * @param delegate     The MessageStream to wrap with the close handler invocation logic
     * @param closeHandler The handler to invoke when the stream is closed or completed
     */
    static <M extends Message> MessageStream.Single<M> single(@Nonnull Single<M> delegate, Runnable closeHandler) {
        return new CloseCallbackMessageStream<>(delegate, closeHandler).first();
    }

    /**
     * Creates an instance of the CloseCallbackMessageStream, calling the given {@code closeHandler} once this
     * stream is closed, or the given {@code delegate} completes.
     *
     * @param delegate     The MessageStream to wrap with the close handler invocation logic
     * @param closeHandler The handler to invoke when the stream is closed or completed
     */

    static <M extends Message> MessageStream.Empty<M> empty(@Nonnull Empty<M> delegate, Runnable closeHandler) {
        return new CloseCallbackMessageStream<>(delegate, closeHandler).ignoreEntries();
    }

    @Override
    public Optional<Entry<M>> next() {
        Optional<Entry<M>> next = delegate().next();
        invokeCloseHandlerIfClosed();
        return next;
    }

    private void invokeCloseHandlerIfClosed() {
        if ((closed.get() || isCompleted()) && !invoked.getAndSet(true)) {
            closeHandler.run();
        }
    }

    @Override
    public Optional<Entry<M>> peek() {
        invokeCloseHandlerIfClosed();
        return delegate().peek();
    }

    @Override
    public void setCallback(@Nonnull Runnable callback) {
        super.setCallback(() -> {
            callback.run();
            invokeCloseHandlerIfClosed();
        });
    }

    @Override
    public void close() {
        closed.set(true);
        super.close();
        invokeCloseHandlerIfClosed();
    }

    @Override
    public boolean hasNextAvailable() {
        invokeCloseHandlerIfClosed();
        return super.hasNextAvailable();
    }
}

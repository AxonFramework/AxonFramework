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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Implementation of the {@link MessageStream} that when the stream completes exceptionally will continue on a
 * {@code MessageStream} returned by the given {@code onError} {@link Function}.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
class OnErrorContinueMessageStream<M extends Message<?>> implements MessageStream<M> {

    private final MessageStream<M> delegate;
    private final AtomicReference<MessageStream<M>> onErrorStream = new AtomicReference<>();
    private final Function<Throwable, MessageStream<M>> onError;
    private final AtomicReference<Runnable> callback = new AtomicReference<>(() -> {
    });

    /**
     * Construct an {@link MessageStream stream} that will proceed on the resulting {@code MessageStream} from the given
     * {@code onError} when the {@code delegate} completes exceptionally
     *
     * @param delegate The delegate {@link MessageStream stream} to proceed from with the result of {@code onError}
     *                 <em>if</em> it completes exceptionally.
     * @param onError  A {@link Function} providing the replacement {@link MessageStream stream} to continue from if the
     *                 given {@code delegate} completes exceptionally.
     */
    OnErrorContinueMessageStream(@Nonnull MessageStream<M> delegate,
                                 @Nonnull Function<Throwable, MessageStream<M>> onError) {
        this.delegate = delegate;
        this.onError = onError;
    }

    @Override
    public Optional<Entry<M>> next() {
        return resolveCurrentDelegate().next();
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        resolveCurrentDelegate().onAvailable(callback);
        this.callback.set(callback);
    }

    private MessageStream<M> resolveCurrentDelegate() {
        if (!delegate.isCompleted() || delegate.error().isEmpty()) {
            return delegate;
        } else if (onErrorStream.get() != null) {
            return onErrorStream.get();
        } else {
            synchronized (this) {
                MessageStream<M> newMessageStream = onErrorStream.updateAndGet((c) -> {
                    if (c == null) {
                        return onError.apply(delegate.error().orElse(null));
                    }
                    return c;
                });
                newMessageStream.onAvailable(callback.get());
                return newMessageStream;
            }
        }
    }

    @Override
    public Optional<Throwable> error() {
        return resolveCurrentDelegate().error();
    }

    @Override
    public boolean isCompleted() {
        return resolveCurrentDelegate().isCompleted();
    }

    @Override
    public boolean hasNextAvailable() {
        return resolveCurrentDelegate().hasNextAvailable();
    }

    @Override
    public void close() {
        resolveCurrentDelegate().close();
    }

}

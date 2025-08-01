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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the {@link MessageStream} that truncates all {@link Entry entries} of the {@code delegate} stream
 * except for the first entry.
 * <p>
 * This allows users to define a {@code MessageStream} of any type and force it to a
 * {@link org.axonframework.messaging.MessageStream.Single} stream instance.
 *
 * @param <M> The type of {@link Message} contained in the singular {@link Entry} of this stream.
 * @author Allard Buijze
 * @since 5.0.0
 */
class TruncateFirstMessageStream<M extends Message<?>>
        extends DelegatingMessageStream<M, M>
        implements MessageStream.Single<M> {

    private final AtomicBoolean consumed = new AtomicBoolean(false);

    /**
     * Constructs the DelegatingMessageStream with given {@code delegate} to receive calls.
     *
     * @param delegate The instance to delegate calls to.
     */
    public TruncateFirstMessageStream(@Nonnull MessageStream<M> delegate) {
        super(delegate);
    }

    @Override
    public Optional<Entry<M>> next() {
        Optional<Entry<M>> next = delegate().next();
        if (next.isPresent() && consumed.compareAndSet(false, true)) {
            close();
            return next;
        }
        return Optional.empty();
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        super.onAvailable(() -> {
            if (!consumed.get()) {
                callback.run();
            }
        });
    }

    @Override
    public Optional<Throwable> error() {
        return consumed.get() ? Optional.empty() : super.error();
    }

    @Override
    public boolean isCompleted() {
        return consumed.get() || super.isCompleted();
    }

    @Override
    public boolean hasNextAvailable() {
        return !consumed.get() && super.hasNextAvailable();
    }

    @Override
    public Optional<Entry<M>> peek() {
        if (!consumed.get()) {
            return delegate().peek();
        }
        return Optional.empty();
    }
}

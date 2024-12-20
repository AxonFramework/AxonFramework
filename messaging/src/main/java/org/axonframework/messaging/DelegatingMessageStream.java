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

import java.util.Optional;

/**
 * Abstract implementation of an MessageStream that delegates calls to a given delegate.
 *
 * @param <DM> The type of Message handled by the delegate
 * @param <RM> The type of Message handled by this MessageStream
 */
public abstract class DelegatingMessageStream<DM extends Message<?>, RM extends Message<?>>
        implements MessageStream<RM> {

    private final MessageStream<DM> delegate;

    /**
     * Constructs the DelegatingMessageStream with given {@code delegate} to receive calls
     *
     * @param delegate The instance to delegate calls to
     */
    public DelegatingMessageStream(@Nonnull MessageStream<DM> delegate) {
        this.delegate = delegate;
    }

    public void onAvailable(@Nonnull Runnable callback) {
        delegate.onAvailable(callback);
    }

    public Optional<Throwable> error() {
        return delegate.error();
    }

    public boolean isCompleted() {
        return delegate.isCompleted();
    }

    public boolean hasNextAvailable() {
        return delegate.hasNextAvailable();
    }

    public void close() {
        delegate.close();
    }

    /**
     * Returns the delegate as provided in the constructor
     *
     * @return the delegate as provided in the constructor
     */
    protected MessageStream<DM> delegate() {
        return delegate;
    }
}

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.messaging.core.MessageStreamUtils.NO_OP_CALLBACK;

/**
 * Abstract implementation of {@link MessageStream} that provides basic state management for completion, errors, and
 * callbacks.
 *
 * @param <M> The type of {@link Message} contained in the {@link Entry entries} of this stream.
 * @author Jan Galinski
 * @since 5.0.3
 */
public abstract class AbstractMessageStream<M extends Message> implements MessageStream<M> {

    private final AtomicReference<Runnable> callback = new AtomicReference<>(NO_OP_CALLBACK);
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);

    @Override
    public void setCallback(@Nonnull Runnable callback) {
        this.callback.set(callback);
        if (hasNextAvailable() || isCompleted()) {
            invokeCallbackSafely();
        }
    }

    /**
     * Invokes the registered callback safely, catching any exceptions and completing the stream with that exception.
     */
    protected void invokeCallbackSafely() {
        try {
            callback.get().run();
        } catch (Throwable t) {
            if (error.compareAndSet(null, t)) {
                completed.set(true);
            }
        }
    }

    /**
     * Completes the stream normally.
     */
    protected void complete() {
        if (completed.compareAndSet(false, true)) {
            invokeCallbackSafely();
        }
    }

    /**
     * Completes the stream with the given {@code throwable}.
     *
     * @param throwable The error that caused the stream to complete.
     */
    protected void completeExceptionally(Throwable throwable) {
        if (error.compareAndSet(null, throwable)) {
            completed.set(true);
            invokeCallbackSafely();
        }
    }

    @Override
    public Optional<Throwable> error() {
        return Optional.ofNullable(error.get());
    }

    @Override
    public boolean isCompleted() {
        return completed.get();
    }
}

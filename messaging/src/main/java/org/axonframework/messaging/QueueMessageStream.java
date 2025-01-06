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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MessageStream implementation that uses a Queue to make elements available to a consumer.
 *
 * @param <M> The type of Message managed by this stream.
 * @author Allard Buijze
 * @since 5.0.0
 */
public class QueueMessageStream<M extends Message<?>> implements MessageStream<M> {

    private static final Runnable NO_OP_CALLBACK = () -> {
    };

    private final BlockingQueue<Entry<M>> queue;
    private final AtomicReference<Runnable> onAvailableCallbackRef = new AtomicReference<>(NO_OP_CALLBACK);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final AtomicReference<Runnable> onConsumeCallback = new AtomicReference<>(NO_OP_CALLBACK);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Constructs an instance with an unbounded queue. Offering {@link Entry entries} will always be possible, as long
     * as memory permits.
     */
    public QueueMessageStream() {
        this(new LinkedBlockingQueue<>());
    }

    /**
     * Construct an instance with given {@code queue} as the underlying queue. Offering and consuming
     * {@link Entry entries} will depend on the semantics of the implementation of the queue.
     * <p>
     * Note that delivery and consumption of entries is done through {@link BlockingQueue#offer(Object)} and
     * {@link BlockingQueue#poll()}, respectively. This means that a queue must be available to buffer elements.
     * Implementations of a {@link java.util.concurrent.TransferQueue} typically don't have this, and will therefore not
     * work.
     *
     * @param queue The queue to use to store {@link Entry entries} in transit from producer to consumer.
     */
    public QueueMessageStream(@Nonnull BlockingQueue<Entry<M>> queue) {
        this.queue = queue;
    }

    /**
     * Add the given {@code message} and accompanying {@code context} available for reading by a consumer. Any callback
     * that has been registered will be notified of the availability of a new {@link Entry entry}.
     * <p>
     * If the underling buffer has insufficient space to store the offered element, the method returns {@code false}.
     *
     * @param message The message to add to the queue.
     * @param context The context to accompany the message.
     * @return {@code true} if the message was successfully buffered. Otherwise {@code false}.
     */
    public boolean offer(@Nonnull M message, @Nonnull Context context) {
        if (queue.offer(new SimpleEntry<>(message, context))) {
            onAvailableCallbackRef.get().run();
            return true;
        }
        return false;
    }

    /**
     * Marks the queue as completed, indicating to any consumer that no more {@link Entry entries} will become
     * available.
     * <p>
     * Note that there is no validation on offering items whether the stream is completed. It is the caller's
     * responsibility to ensure no {@link Message Messages} are {@link #offer(Message, Context) offered} after
     * completion.
     */
    public void complete() {
        completed.set(true);
        onAvailableCallbackRef.get().run();
    }

    /**
     * Marks the queue as completed exceptionally caused by given {@code error}, indicating to any consumer that no more
     * messages will become available.
     * <p>
     * Note that there is no validation on offering items whether the stream is completed. It is the caller's
     * responsibility to ensure no Messages are {@link #offer(Message, Context) offered} after completion.
     *
     * @param error The cause of the exceptional completion
     */
    public void completeExceptionally(@Nonnull Throwable error) {
        errorRef.set(error);
        completed.set(true);
        onAvailableCallbackRef.get().run();
    }

    /**
     * Registers given {@code callback} to be invoked when {@link Entry entries} have been consumed from the underlying
     * queue. Any previously registered callback will be replaced.
     * <p>
     * The given {@code callback} is also notified when the consumer has requested to {@link #close()} this stream.
     *
     * @param callback The callback to invoke when {@link Entry entries} are consumed.
     */
    public void onConsumeCallback(@Nonnull Runnable callback) {
        this.onConsumeCallback.set(callback);
    }

    /**
     * Whether the consumer has requested to {@link #close()} this stream. This is a signal to producing components to
     * stop emitting more {@link Message messages}, complete the stream, and release any sources associated with this
     * stream.
     *
     * @return {@code true} if a close was requested, otherwise {@code false}.
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public Optional<Entry<M>> next() {
        Entry<M> poll = queue.poll();
        if (poll != null) {
            onConsumeCallback.get().run();
        }
        return Optional.ofNullable(poll);
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        onAvailableCallbackRef.set(callback);
        if (!queue.isEmpty() || isCompleted()) {
            callback.run();
        }
    }

    @Override
    public Optional<Throwable> error() {
        return Optional.ofNullable(errorRef.get());
    }

    @Override
    public boolean isCompleted() {
        return queue.isEmpty() && completed.get();
    }

    @Override
    public boolean hasNextAvailable() {
        return !queue.isEmpty();
    }

    @Override
    public void close() {
        this.closed.set(true);
        onConsumeCallback.get().run();
    }
}

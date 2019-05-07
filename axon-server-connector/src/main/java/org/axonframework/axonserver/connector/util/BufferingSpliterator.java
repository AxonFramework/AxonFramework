/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Converts the push interface for QueryResponses from Axon Server to a pull interface to be used by Axon Framework.
 * Uses a queue to cache messages.
 *
 * @param <R> a generic defining the type this queue will contain
 * @author Marc Gathier
 * @since 4.0
 */
public class BufferingSpliterator<R> implements Spliterator<R> {

    private final static Logger logger = LoggerFactory.getLogger(BufferingSpliterator.class);

    private final long deadline;
    private final BlockingQueue<WrappedElement<R>> blockingQueue;
    private AtomicBoolean finished = new AtomicBoolean(false);
    private AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Instantiate a buffering {@link Spliterator} implementation. The given {@code deadline} indicates the
     * instant until which the {@link #tryAdvance(Consumer)} method can return a positive result. After the
     * deadline, {@code tryAdvance(Consumer)} will always return {@code false}.
     * <p>
     * The queue capacity of the backing queue is {@code Integer.MAX_VALUE}.
     *
     * @param deadline the timestamp in millis since epoch until when data may be served by this instance
     */
    public BufferingSpliterator(Instant deadline) {
        this(deadline, Integer.MAX_VALUE);
    }

    /**
     * Instantiate a buffering {@link Spliterator} implementation with a queue capacity of
     * {@code Integer.MAX_VALUE}.
     */
    public BufferingSpliterator() {
        this(Long.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Instantiate a buffering {@link Spliterator} implementation. The given {@code deadline} indicates the
     * instant until which the {@link #tryAdvance(Consumer)} method can return a positive result. After the
     * deadline, {@code tryAdvance(Consumer)} will always return {@code false}.
     * <p>
     * {@code bufferCapacity} defines the capacity of the underlying buffer. Offering operations will block
     * until space is available on the buffer
     *
     * @param deadline       the timestamp in millis since epoch until when data may be served by this instance
     * @param bufferCapacity The number of items allowed in this buffer before suppliers are blocked
     */
    public BufferingSpliterator(Instant deadline, int bufferCapacity) {
        this(deadline.toEpochMilli(), bufferCapacity);
    }

    /**
     * Instantiate a buffering {@link Spliterator} implementation with the given {@code bufferCapacity} for the
     * underlying buffer. Offering operations will block until space is available on the buffer
     *
     * @param bufferCapacity The number of items allowed in this buffer before suppliers are blocked
     */
    public BufferingSpliterator(int bufferCapacity) {
        this(Long.MAX_VALUE, bufferCapacity);
    }

    private BufferingSpliterator(long deadline, int bufferCapacity) {
        this.deadline = deadline;
        this.blockingQueue = new LinkedBlockingQueue<>(bufferCapacity);
    }

    @Override
    public boolean tryAdvance(Consumer<? super R> action) {
        WrappedElement<R> element = null;
        try {
            long timeLeft = deadline == Long.MAX_VALUE ? Long.MAX_VALUE : deadline - System.currentTimeMillis();
            if (!finished.get() && timeLeft > 0) {
                element = blockingQueue.poll(timeLeft, TimeUnit.MILLISECONDS);
                if (element != null) {
                    if (element.stop) {
                        finished.set(true);
                        blockingQueue.clear();
                        return false;
                    }
                    if (element.wrapped != null) {
                        action.accept(element.wrapped);
                        return true;
                    }
                }
            } else {
                cancelled.set(true);
                finished.set(true);
            }
        } catch (InterruptedException e) {
            cancel(e);
            Thread.currentThread().interrupt();
            logger.warn("Interrupted tryAdvance", e);
            return false;
        }
        return element != null;
    }

    @Override
    public Spliterator<R> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
        return ORDERED;
    }

    /**
     * Add an element to the queue, if possible.
     *
     * @param object an {@code R} to be added to the queue
     * @return {@code true} if the object was accepted, or {@code false} if the spliterator does not accept new
     * messages
     */
    public boolean put(R object) {
        try {
            if (!cancelled.get()) {
                blockingQueue.put(new WrappedElement<>(object));
            } else {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Put operation was interrupted", e);
            throw new RuntimeException(e);
        }
        return true;
    }

    /**
     * Cancel further element retrieval of this queue.
     *
     * @param t a {@link Throwable} marking why the queue was canceled. Can be {@code null}
     */
    public void cancel(Throwable t) {
        try {
            if (!cancelled.get()) {
                blockingQueue.put(new WrappedElement<>(true, t));
                cancelled.set(true);
            }
        } catch (InterruptedException e) {
            logger.warn("Cancel operation was interrupted", e);
        }
    }

    private class WrappedElement<W> {

        private final W wrapped;
        private final boolean stop;
        private final Throwable exception;

        WrappedElement(W wrapped) {
            this.wrapped = wrapped;
            this.stop = false;
            this.exception = null;
        }

        WrappedElement(boolean stop, Throwable exception) {
            this.wrapped = null;
            this.stop = stop;
            this.exception = exception;
        }
    }
}

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

package org.axonframework.axonserver.connector.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Converts the push interface for QueryResponses from Axon Server to a pull interface to be used by Axon Framework.
 * Uses a queue to cache messages.
 *
 * @param <R> a generic defining the type this queue will contain
 * @author Marc Gathier
 * @since 4.0
 */
public class QueueBackedSpliterator<R> implements Spliterator<R> {

    private final static Logger logger = LoggerFactory.getLogger(QueueBackedSpliterator.class);

    private final long timeoutMs;
    private final BlockingQueue<WrappedElement<R>> blockingQueue;

    /**
     * Instantiate a queue backed {@link Spliterator} implementation. The given {@code timeout} combined with the
     * {@code timeUnit} define for how long the {@link #tryAdvance(Consumer)} method can be invoked. After the timeout
     * has exceeded, {@code tryAdvance(Consumer)} defaults to {@code false}.
     *
     * @param timeout  a {@code long} defining the timeout, together with the provided {@code timeUnit}
     * @param timeUnit a {@link TimeUnit} defining the unit of time for the provided {@code timeout}
     */
    public QueueBackedSpliterator(long timeout, TimeUnit timeUnit) {
        timeoutMs = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        blockingQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public boolean tryAdvance(Consumer<? super R> action) {
        WrappedElement<R> element = null;
        try {
            long remaining = timeoutMs - System.currentTimeMillis();
            if (remaining > 0) {
                element = blockingQueue.poll(timeoutMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if (element != null) {
                    if (element.stop) {
                        return false;
                    }
                    if (element.wrapped != null) {
                        action.accept(element.wrapped);
                    }
                }
            }
        } catch (InterruptedException e) {
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
        return 0;
    }

    @Override
    public int characteristics() {
        return 0;
    }

    /**
     * Add an element to the queue.
     *
     * @param object an {@code R} to be added to the queue
     */
    public void put(R object) {
        try {
            blockingQueue.put(new WrappedElement<>(object));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Put operation was interrupted", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Cancel further element retrieval of this queue.
     *
     * @param t a {@link Throwable} marking why the queue was canceled. Can be {@code null}
     */
    public void cancel(Throwable t) {
        try {
            blockingQueue.put(new WrappedElement<>(true, t));
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

/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.common.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread safe buffer for storing incoming Kafka messages in sorted order defined via {@link Comparable}.
 *
 * @param <E> element type.
 * @author Nakul Mishra
 * @since 3.3
 */
public class SortedKafkaMessageBuffer<E extends Comparable & KafkaMetadataProvider> implements Buffer<E> {

    private static final Logger logger = LoggerFactory.getLogger(SortedKafkaMessageBuffer.class);

    /**
     * Data structure
     */
    private final ConcurrentSkipListSet<E> delegate;

    /**
     * Main lock guarding all access
     */
    private final ReentrantLock lock;

    /**
     * Condition for waiting takes
     */
    private final Condition notEmpty;

    /**
     * Condition for waiting puts
     */
    private final Condition notFull;

    /**
     * Max buffer size
     */
    private final int capacity;

    /**
     * Number of messages in the buffer
     */
    private int count;


    public SortedKafkaMessageBuffer() {
        this(1_000);
    }

    /**
     * @param capacity the capacity of this buffer.
     */
    public SortedKafkaMessageBuffer(int capacity) {
        Assert.isTrue(capacity > 0, () -> "Capacity may not be <= 0");
        this.delegate = new ConcurrentSkipListSet<>();
        this.lock = new ReentrantLock();
        this.notEmpty = lock.newCondition();
        this.notFull = lock.newCondition();
        this.capacity = capacity;
    }

    /**
     * Inserts the specified message in this buffer, waiting
     * for space to become available if the buffer is full.
     *
     * @throws InterruptedException
     * @throws NullPointerException
     */
    @Override
    public void put(E e) throws InterruptedException {
        Assert.notNull(e, () -> "Element may not be empty");
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            doPut(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void putAll(Collection<E> c) throws InterruptedException {
        Assert.notNull(c, () -> "Element may not be empty");
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (E e : c) {
                doPut(e);
            }
        } finally {
            lock.unlock();
        }
    }

    private void doPut(E e) throws InterruptedException {
        while (this.count == this.capacity) {
            this.notFull.await();
        }
        add(e);
        if (logger.isDebugEnabled()) {
            logger.debug("buffer state after appending {}", e);
            for (E message : delegate) {
                logger.debug("partition:{}, offset:{}, timestamp:{}, payload:{}",
                             message.partition(),
                             message.offset(),
                             message.timestamp(),
                             message.value());
            }
        }
    }

    /**
     * Retrieves and removes the first message of this buffer, waiting up to the
     * specified wait time if necessary for a message to become available.
     *
     * @param timeout how long to wait before giving up, in units of
     *                {@code unit}
     * @param unit    a {@code TimeUnit} determining how to interpret the
     *                {@code timeout} parameter
     * @return the first message of this buffer, or {@code null} if the
     * specified waiting time elapses before a message is available
     *
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (this.count == 0) {
                if (nanos <= 0) {
                    return null;
                }
                nanos = this.notEmpty.awaitNanos(nanos);
            }
            E removed = remove();
            if (logger.isDebugEnabled()) {
                logger.debug("buffer state after removing {}", removed);
                for (E message : delegate) {
                    logger.debug("partition:{}, offset:{}, timestamp:{}, payload:{}",
                                 message.partition(),
                                 message.offset(),
                                 message.value(),
                                 message.timestamp());
                }
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the first messages of this buffer, waiting if necessary
     * until a message becomes available.
     *
     * @return the first message of this buffer.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (this.count == 0) {
                this.notEmpty.await();
            }
            return remove();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves, but does not remove, the first message of this buffer, or returns null if this buffer is empty.
     *
     * @return the message.
     */
    @Override
    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return this.count > 0 ? this.delegate.first() : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts message, advances, and signals.
     * Call only when holding lock.
     */
    private void add(E x) {
        if (this.delegate.add(x)) {
            this.count++;
            this.notEmpty.signal();
        }
    }

    /**
     * Extracts message and signals.
     * Call only when holding lock.
     */
    private E remove() {
        E x = this.delegate.pollFirst();
        if (x != null) {
            this.count--;
            this.notFull.signal();
        }
        return x;
    }

    /**
     * Returns the number of elements in this buffer.
     *
     * @return the number of elements in this buffer
     */
    @Override
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return this.count;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return this.count == 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of additional elements that this buffer can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this buffer
     * less the current {@code size} of this buffer.
     * <p>
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    @Override
    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return this.capacity - count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all of the messages from this buffer.
     */
    @Override
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            this.delegate.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "SortedKafkaMessageBuffer:" + this.delegate;
    }
}

/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.time.Instant;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Generic implementation of the {@link RetryableDeadLetter} allowing any type of {@link Message} to be dead lettered.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
class GenericRetryableDeadLetter<M extends Message<?>> implements RetryableDeadLetter<M> {

    private final DeadLetter<M> delegate;
    private final Instant retryAt;
    private final int numberOfRetries;
    private final Consumer<RetryableDeadLetter<M>> acknowledge;
    private final BiConsumer<RetryableDeadLetter<M>, Throwable> requeue;

    public GenericRetryableDeadLetter(RetryableDeadLetter<M> delegate,
                                      Instant retryAt,
                                      Consumer<RetryableDeadLetter<M>> acknowledge,
                                      BiConsumer<RetryableDeadLetter<M>, Throwable> requeue) {
        this(delegate, retryAt, delegate.numberOfRetries() + 1, acknowledge, requeue);
    }

    public GenericRetryableDeadLetter(DeadLetter<M> delegate,
                                      Instant retryAt,
                                      int numberOfRetries,
                                      Consumer<RetryableDeadLetter<M>> acknowledge,
                                      BiConsumer<RetryableDeadLetter<M>, Throwable> requeue) {
        this.delegate = delegate;
        this.numberOfRetries = numberOfRetries;
        this.retryAt = retryAt;
        this.acknowledge = acknowledge;
        this.requeue = requeue;
    }

    @Override
    public String identifier() {
        return delegate.identifier();
    }

    @Override
    public QueueIdentifier queueIdentifier() {
        return delegate.queueIdentifier();
    }

    @Override
    public M message() {
        return delegate.message();
    }

    @Nullable
    @Override
    public Cause cause() {
        return delegate.cause();
    }

    @Override
    public Instant enqueuedAt() {
        return delegate.enqueuedAt();
    }

    @Override
    public void evict() {
        delegate.evict();
    }

    @Override
    public void release() {
        delegate.release();
    }

    @Nullable
    @Override
    public Instant retryAt() {
        return retryAt;
    }

    @Override
    public int numberOfRetries() {
        return numberOfRetries;
    }

    @Override
    public void acknowledge() {
        acknowledge.accept(this);
    }

    @Override
    public void requeue(Throwable cause) {
        requeue.accept(this, cause);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        // Check does not include the retriedAt, numberOfRetries, acknowledge,
        //  and requeue operations to allow easy letter removal in the DeadLetterQueue.
        //noinspection unchecked
        GenericRetryableDeadLetter<M> that = (GenericRetryableDeadLetter<M>) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, retryAt, numberOfRetries);
    }

    @Override
    public String toString() {
        return "GenericRetryableDeadLetter{" +
                "identifier='" + delegate.identifier() + '\'' +
                ", queueIdentifier=" + delegate.queueIdentifier() +
                ", message=" + delegate.message() +
                ", cause=" + delegate.cause() +
                ", enqueueAt=" + delegate.enqueuedAt() +
                ", retryAt=" + retryAt +
                ", numberOfRetries=" + numberOfRetries +
                '}';
    }
}

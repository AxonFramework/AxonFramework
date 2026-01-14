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

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.core.Message;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import jakarta.annotation.Nonnull;

/**
 * Adapter that wraps a {@link SyncSequencedDeadLetterQueue} and provides the asynchronous
 * {@link SequencedDeadLetterQueue} interface.
 * <p>
 * This adapter allows synchronous dead letter queue implementations (such as JPA or JDBC-based queues) to be used
 * where the asynchronous {@link SequencedDeadLetterQueue} interface is expected.
 * <p>
 * All operations are executed synchronously and wrapped in {@link CompletableFuture} instances. Exceptions thrown by
 * the delegate are captured in failed futures.
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead letters} within this queue.
 * @author Mateusz Nowak
 * @see SyncSequencedDeadLetterQueue
 * @see SequencedDeadLetterQueue
 * @since 5.0.0
 */
public class SyncToAsyncDeadLetterQueueAdapter<M extends Message> implements SequencedDeadLetterQueue<M> {

    private final SyncSequencedDeadLetterQueue<M> delegate;

    /**
     * Constructs a new adapter wrapping the given synchronous dead letter queue.
     *
     * @param delegate The synchronous dead letter queue to wrap.
     */
    public SyncToAsyncDeadLetterQueueAdapter(@Nonnull SyncSequencedDeadLetterQueue<M> delegate) {
        this.delegate = delegate;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> enqueue(@Nonnull Object sequenceIdentifier,
                                           @Nonnull DeadLetter<? extends M> letter) {
        try {
            delegate.enqueue(sequenceIdentifier, letter);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> enqueueIfPresent(@Nonnull Object sequenceIdentifier,
                                                       @Nonnull Supplier<DeadLetter<? extends M>> letterBuilder) {
        try {
            boolean result = delegate.enqueueIfPresent(sequenceIdentifier, letterBuilder);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> evict(@Nonnull DeadLetter<? extends M> letter) {
        try {
            delegate.evict(letter);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> requeue(@Nonnull DeadLetter<? extends M> letter,
                                           @Nonnull UnaryOperator<DeadLetter<? extends M>> letterUpdater) {
        try {
            delegate.requeue(letter, letterUpdater);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> contains(@Nonnull Object sequenceIdentifier) {
        try {
            boolean result = delegate.contains(sequenceIdentifier);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Iterable<DeadLetter<? extends M>>> deadLetterSequence(
            @Nonnull Object sequenceIdentifier) {
        try {
            Iterable<DeadLetter<? extends M>> result = delegate.deadLetterSequence(sequenceIdentifier);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Iterable<Iterable<DeadLetter<? extends M>>>> deadLetters() {
        try {
            Iterable<Iterable<DeadLetter<? extends M>>> result = delegate.deadLetters();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> isFull(@Nonnull Object sequenceIdentifier) {
        try {
            boolean result = delegate.isFull(sequenceIdentifier);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> size() {
        try {
            long result = delegate.size();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> sequenceSize(@Nonnull Object sequenceIdentifier) {
        try {
            long result = delegate.sequenceSize(sequenceIdentifier);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Long> amountOfSequences() {
        try {
            long result = delegate.amountOfSequences();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Boolean> process(
            @Nonnull Predicate<DeadLetter<? extends M>> sequenceFilter,
            @Nonnull Function<DeadLetter<? extends M>, CompletableFuture<EnqueueDecision<M>>> processingTask) {
        try {
            // Convert async processing task to sync by joining the future
            Function<DeadLetter<? extends M>, EnqueueDecision<M>> syncTask =
                    letter -> processingTask.apply(letter).join();
            boolean result = delegate.process(sequenceFilter, syncTask);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> clear() {
        try {
            delegate.clear();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Returns the delegate synchronous dead letter queue.
     *
     * @return The wrapped {@link SyncSequencedDeadLetterQueue}.
     */
    public SyncSequencedDeadLetterQueue<M> getDelegate() {
        return delegate;
    }
}

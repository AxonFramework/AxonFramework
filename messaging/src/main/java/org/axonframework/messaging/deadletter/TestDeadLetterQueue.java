package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

public class TestDeadLetterQueue<T extends Message<?>> implements RetryableDeadLetterQueue<RetryableDeadLetter<T>, T> {

    DeadLetterQueue<RetryableDeadLetter<T>, T> delegate;
    RetryPolicy<T> retryPolicy;

    @Override
    public RetryableDeadLetter<T> enqueue(@Nonnull QueueIdentifier identifier, @Nonnull T deadLetter, Throwable cause)
            throws DeadLetterQueueOverflowException {
        RetryableDeadLetter<T> letter = delegate.enqueue(identifier, deadLetter, cause);

        RetryDecision decision = retryPolicy.decide(letter, cause);
        return null;
    }

    @Override
    public void enqueue(RetryableDeadLetter<T> letter) throws DeadLetterQueueOverflowException {

    }

    @Override
    public boolean contains(@Nonnull QueueIdentifier identifier) {
        return delegate.contains(identifier);
    }

    @Override
    public Iterable<RetryableDeadLetter<T>> deadLetters(@Nonnull QueueIdentifier identifier) {
        return delegate.deadLetters(identifier);
    }

    @Override
    public Iterable<DeadLetterSequence<T>> deadLetterSequences() {
        return delegate.deadLetterSequences();
    }

    @Override
    public boolean isFull(@Nonnull QueueIdentifier queueIdentifier) {
        return delegate.isFull(queueIdentifier);
    }

    @Override
    public long maxQueues() {
        return 0;
    }

    @Override
    public long maxQueueSize() {
        return 0;
    }

    @Override
    public Optional<RetryableDeadLetter<T>> take(@Nonnull String group) {
        return Optional.empty();
    }

    @Override
    public void clear(@Nonnull Predicate<QueueIdentifier> queueFilter) {

    }

    @Override
    public void release(@Nonnull Predicate<QueueIdentifier> queueFilter) {

    }

    @Override
    public void onAvailable(@Nonnull String group, @Nonnull Runnable callback) {

    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return null;
    }
}

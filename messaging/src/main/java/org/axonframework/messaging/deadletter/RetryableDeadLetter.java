package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Interface describing a dead-lettered {@link Message} implementation of generic type {@code T}.
 * <p>
 * The time of storing the {@link #message()} is kept through {@link #enqueuedAt()}. This letter can be regarded for
 * evaluation once the {@code #expiresAt()} time is reached. Upon successful evaluation the letter can be cleared
 * through {@code #acknowledge()}. The {@code #requeue()} method should be used to signal evaluation failed, reentering
 * the letter into its queue.
 *
 * @param <M> The type of {@link Message} represented by this interface.
 * @author Steven van Beelen
 * @since 4.6.0
 */
public interface RetryableDeadLetter<T extends Message<?>> extends DeadLetter<T> {

    /**
     * The moment in time when this letter will expire. Should be used to deduce whether the letter is ready to be
     * evaluated. Will equal the {@link #enqueuedAt()} value if this letter is enqueued as part of a sequence to ensure
     * it is available right after a previous letter within the same sequence.
     *
     * @return The moment in time when this letter will expire.
     */
    // TODO: 14-07-22 adjust javadoc
    // TODO: 14-07-22 Should we store the RetryDecision, so that take() may clear out entries and find the one to use?
    // This means the RetryPolicy becomes part of the construction of the DeadLetter.
    // Or, that the RetryDecision isn't present on the DeadLetter interface, but on the RetryableDeadLetter interface(?)
    // A RetryableDeadLetter interface would be a fair spot to maintain numberOfRetries, retriedAt, acknowledge and requeue too
    @Nullable
    Instant retryAt();


    /**
     * The number of retries this {@link DeadLetter dead-letter} has gone through.
     *
     * @return The number of retries this {@link DeadLetter dead-letter} has gone through.
     */
    int numberOfRetries();

    /**
     * Acknowledges this {@link DeadLetter dead-letter} as successfully evaluated. This operation will remove this
     * letter from its queue.
     */
    void acknowledge();

    /**
     * Reenters this {@link DeadLetter dead-letter} in the queue it originates from. This method should be used to
     * signal the evaluation failed.
     * <p>
     * The operation will adjust the {@code #expiresAt()} to the current time and increment the
     * {@link #numberOfRetries()}. This operation might remove this letter from the {@link DeadLetterQueue queue} it
     * originated from.
     */
    void requeue(Throwable cause);
}

package org.axonframework.messaging.deadletter;

import org.axonframework.messaging.Message;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

public interface RetryableDeadLetterQueue<D extends RetryableDeadLetter<M>, M extends Message<?>> extends DeadLetterQueue<D, M> {

    /**
     * Release all {@link DeadLetter dead-letters} within this queue that match the given {@code queueFilter}.
     * <p>
     * This makes the matching letters ready to be {@link #take(String) taken}. Furthermore, it signals any matching
     * (based on the {@code group} name) callbacks registered through {@link #onAvailable(String, Runnable)}.
     *
     * @param queueFilter A lambda selecting the letters within this queue to be released.
     */
    void release(@Nonnull Predicate<QueueIdentifier> queueFilter);

    /**
     * Release all {@link DeadLetter dead-letters} within this queue that match the given {@code group}.
     * <p>
     * This makes the matching letters ready to be {@link #take(String) taken}. Furthermore, it signals any matching
     * (based on the {@code group} name) callbacks registered through {@link #onAvailable(String, Runnable)}.
     *
     * @param group The group descriptor of a {@link QueueIdentifier} to release all {@link DeadLetter dead-letters}
     *              for.
     */
    default void release(@Nonnull String group) {
        release(queueIdentifier -> Objects.equals(queueIdentifier.group(), group));
    }

    /**
     * Release all {@link DeadLetter dead-letters} within this queue.
     * <p>
     * This makes the letters ready to be {@link #take(String) taken}. Furthermore, it signals any callbacks registered
     * through {@link #onAvailable(String, Runnable)}.
     */
    default void release() {
        release(queueIdentifier -> true);
    }

    /**
     * Set the given {@code callback} for the given {@code group} to be invoked when {@link DeadLetter dead-letters} are
     * ready to be {@link #take(String) taken} from the queue. Dead-letters may be released earlier through
     * {@link #release(Predicate)} to automatically trigger the {@code callback} if the {@code group} matches.
     *
     * @param group    The group descriptor of a {@link QueueIdentifier} to register a {@code callback} for.
     * @param callback The operation to run whenever {@link DeadLetter dead-letters} are released and ready to be
     *                 taken.
     */
    void onAvailable(@Nonnull String group, @Nonnull Runnable callback);

    /**
     * Shutdown this queue. Invoking this operation ensure any
     * {@link #onAvailable(String, Runnable) registered callbacks} that are active are properly stopped too.
     *
     * @return A {@link CompletableFuture} that's completed asynchronously once all active on available callbacks have
     * completed.
     */
    CompletableFuture<Void> shutdown();
}

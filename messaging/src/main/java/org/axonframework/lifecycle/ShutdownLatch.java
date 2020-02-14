package org.axonframework.lifecycle;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A latch implementation to be used in shutdown scenarios. Operations to wait for can be added by invoking {@link
 * #increment()}. An added operation should always be removed through {@link #decrement()} once it has completed, as
 * {@link #await()} will otherwise block indefinitely. If the latch is waited on through {@link #await()}, new
 * operations can no longer be added.
 *
 * @author Steven van Beelen
 * @since 4.3
 */
public class ShutdownLatch {

    private final AtomicInteger operationCounter = new AtomicInteger(0);
    private CompletableFuture<Void> latch;

    /**
     * Add an operation this latch should wait on before opening up. If this operation is called whilst {@link #await()}
     * is already called an {@link IllegalStateException} will be thrown.
     *
     * @throws IllegalStateException if {@link #await()} has been called prior to invoking this method
     */
    public void increment() {
        if (isShuttingDown()) {
            throw new IllegalStateException("No new operations can be added, since this latched is waited for.");
        }
        operationCounter.incrementAndGet();
    }

    /**
     * Remove an operation this latch waits on. This method should be invoked once the added operations has ended. This
     * method will complete the latch if {@link #await()} has been invoked abd all operations are removed.
     */
    public void decrement() {
        if (operationCounter.decrementAndGet() <= 0 && isShuttingDown()) {
            latch.complete(null);
        }
    }

    /**
     * Check whether this {@link ShutdownLatch} is waited on. The exception retrieved from the {@code exceptionSupplier}
     * will be thrown if this latch is shutting down.
     *
     * @param exceptionSupplier a {@link Supplier} of a {@link RuntimeException} to throw if this latch is waited on
     */
    public void isShuttingDown(Supplier<RuntimeException> exceptionSupplier) {
        if (isShuttingDown()) {
            throw exceptionSupplier.get();
        }
    }

    /**
     * Check whether this {@link ShutdownLatch} is waited on.
     *
     * @return {@code true} if the latch is waited on, {@code false} otherwise
     */
    public boolean isShuttingDown() {
        return latch != null;
    }

    /**
     * Wait for this latch to complete all the operations given to it through {@link #increment()}.
     *
     * @return a {@link CompletableFuture} which completes once all operations are done
     */
    public CompletableFuture<Void> await() {
        latch = new CompletableFuture<>();
        return latch;
    }

    /**
     * Wait for this latch to complete all the operations given to it through {@link #increment()}. If the given {@code
     * duration} has passed the returned {@link CompletableFuture} will be completed exceptionally.
     *
     * @param duration the time to wait for this latch to complete
     * @return a {@link CompletableFuture} which completes successfully once all operations are done or exceptionally
     * after the given {@code duration}
     */
    public CompletableFuture<Void> await(Duration duration) {
        CompletableFuture<Void> latch = await();
        CompletableFuture.runAsync(() -> {
            try {
                latch.get(duration.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                latch.completeExceptionally(e);
            }
        });
        return latch;
    }
}

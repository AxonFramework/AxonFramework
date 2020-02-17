package org.axonframework.lifecycle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A latch implementation to be used in shutdown scenarios. Activities to wait for can be added by invoking {@link
 * #registerActivity()}. An registered activity should always shutdown through the returned {@link ActivityHandle}'s
 * {@link ActivityHandle#end()} method once it has completed. Otherwise {@link #initiateShutdown()} will block
 * indefinitely. If the latch is waited on through {@link #initiateShutdown()}, new operations can no longer be added.
 *
 * @author Steven van Beelen
 * @since 4.3
 */
public class ShutdownLatch {

    private final AtomicInteger operationCounter = new AtomicInteger(0);
    private volatile CompletableFuture<Void> latch;

    /**
     * Add an activity this latch should wait on before opening up. If this operation is invoked whilst {@link
     * #initiateShutdown()} has already been called a {@link ShutdownInProgressException} will be thrown.
     *
     * @return an {@link ActivityHandle} to {@link ActivityHandle#end()} the registered activity once it is done
     * @throws ShutdownInProgressException if {@link #initiateShutdown()} has been called prior to invoking this method
     */
    public ActivityHandle registerActivity() {
        ifShuttingDown(ShutdownInProgressException::new);
        operationCounter.incrementAndGet();
        return new ActivityHandle();
    }

    /**
     * Check whether this {@link ShutdownLatch} is waited on. The exception retrieved from the {@code exceptionSupplier}
     * will be thrown if this latch is shutting down.
     *
     * @param exceptionSupplier a {@link Supplier} of a {@link RuntimeException} to throw if this latch is waited on
     */
    public void ifShuttingDown(Supplier<RuntimeException> exceptionSupplier) {
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
     * Initiate the shutdown of this latch. The returned {@link CompletableFuture} will complete once all activities
     * have been ended.
     *
     * @return a {@link CompletableFuture} which completes once all activities are done
     */
    public CompletableFuture<Void> initiateShutdown() {
        latch = new CompletableFuture<>();
        return latch;
    }

    /**
     * A handle for an activity registered to a {@link ShutdownLatch}. The {@link ActivityHandle#end()} method should be
     * called if the registered activity is finalized.
     */
    public class ActivityHandle implements AutoCloseable {

        private AtomicBoolean ended = new AtomicBoolean(false);

        /**
         * Mark this activity as being finalized. This method should be invoked once the registered activity (through
         * {@link ShutdownLatch#registerActivity()}) has ended. This method will complete the {@link ShutdownLatch} if
         * {@link ShutdownLatch#initiateShutdown()} has been invoked and all activities have ended.
         *
         * @return {@code true} on the first call of this method and {@code false} for subsequent invocations
         */
        public boolean end() {
            boolean firstInvocation = !ended.get();
            if (firstInvocation) {
                ended.getAndSet(true);
            }

            if (operationCounter.decrementAndGet() <= 0 && isShuttingDown()) {
                latch.complete(null);
            }

            return firstInvocation;
        }

        /**
         * Close this {@link ActivityHandle} by invoking {@link #end()}.
         * <p>
         * {@inheritDoc}
         */
        @Override
        public void close() {
            end();
        }
    }
}

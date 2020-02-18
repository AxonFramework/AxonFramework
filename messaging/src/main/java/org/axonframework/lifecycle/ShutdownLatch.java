package org.axonframework.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A latch implementation to be used in shutdown scenarios. Activities to wait for can be added by invoking {@link
 * #registerActivity()}. A registered activity should always shutdown through the returned {@link ActivityHandle}'s
 * {@link ActivityHandle#end()} method once it has completed. Otherwise {@link #initiateShutdown()} will block
 * indefinitely. If the latch is waited on through {@link #initiateShutdown()}, new operations can no longer be
 * registered.
 *
 * @author Steven van Beelen
 * @since 4.3
 */
public class ShutdownLatch {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final AtomicInteger operationCounter = new AtomicInteger(0);
    private final AtomicReference<CompletableFuture<Void>> latch = new AtomicReference<>();

    /**
     * Initialize this {@link ShutdownLatch}.  If the latch was already closed through {@link #initiateShutdown()}, then
     * that operation will be canceled.
     */
    public void initialize() {
        CompletableFuture<Void> existingLatch = latch.getAndSet(null);
        if (existingLatch != null) {
            logger.warn("Latch is being initialized whilst already shutting down");
            existingLatch.cancel(true);
        }
    }

    /**
     * Add an activity this latch should wait on before opening up. If this operation is invoked whilst {@link
     * #initiateShutdown()} has already been called a {@link ShutdownInProgressException} will be thrown.
     *
     * @return an {@link ActivityHandle} to {@link ActivityHandle#end()} the registered activity once it is done
     * @throws ShutdownInProgressException if {@link #initiateShutdown()} has been called prior to invoking this method
     */
    public ActivityHandle registerActivity() {
        ifShuttingDown(ShutdownInProgressException::new);
        int counter = operationCounter.getAndIncrement();

        if (counter == 0 && latch.get() != null) {
            operationCounter.getAndDecrement();
            throw new ShutdownInProgressException();
        }
        return new ActivityHandle();
    }

    /**
     * Check whether this {@link ShutdownLatch} is shutting down. The given {@code exceptionMessage} is used in the
     * thrown {@link ShutdownInProgressException}, if this latch is shutting down.
     *
     * @param exceptionMessage the message used for the {@link ShutdownInProgressException} to throw if this latch is
     *                         shutting down
     */
    public void ifShuttingDown(String exceptionMessage) {
        ifShuttingDown(() -> new ShutdownInProgressException(exceptionMessage));
    }

    /**
     * Check whether this {@link ShutdownLatch} is shutting down. The exception retrieved from the {@code
     * exceptionSupplier} will be thrown if this latch is shutting down.
     *
     * @param exceptionSupplier a {@link Supplier} of a {@link RuntimeException} to throw if this latch is waited on
     */
    public void ifShuttingDown(Supplier<RuntimeException> exceptionSupplier) {
        if (isShuttingDown()) {
            throw exceptionSupplier.get();
        }
    }

    /**
     * Check whether this {@link ShutdownLatch} is shutting down.
     *
     * @return {@code true} if the latch is waited on, {@code false} otherwise
     */
    public boolean isShuttingDown() {
        return latch.get() != null;
    }

    /**
     * Initiate the shutdown of this latch. The returned {@link CompletableFuture} will complete once all activities
     * have been ended or complete immediately if no activities are active.
     *
     * @return a {@link CompletableFuture} which completes once all activities are done
     */
    public CompletableFuture<Void> initiateShutdown() {
        CompletableFuture<Void> newLatch = new CompletableFuture<>();
        CompletableFuture<Void> existingLatch = latch.getAndUpdate(previous -> previous == null ? newLatch : previous);

        if (existingLatch == null) {
            if (operationCounter.get() == 0) {
                newLatch.complete(null);
            }
            return newLatch;
        }

        return existingLatch;
    }

    /**
     * A handle for an activity registered to a {@link ShutdownLatch}. The {@link ActivityHandle#end()} method should be
     * called if the registered activity is finalized.
     */
    public class ActivityHandle implements AutoCloseable {

        private final AtomicBoolean ended = new AtomicBoolean(false);

        /**
         * Mark this activity as being finalized. This method should be invoked once the registered activity (through
         * {@link ShutdownLatch#registerActivity()}) has ended. This method will complete the {@link ShutdownLatch} if
         * {@link ShutdownLatch#initiateShutdown()} has been invoked and all activities have ended.
         */
        public void end() {
            boolean firstInvocation = ended.compareAndSet(false, true);
            if (firstInvocation && operationCounter.decrementAndGet() <= 0) {
                CompletableFuture<Void> currentLatch = latch.get();
                if (currentLatch != null) {
                    currentLatch.complete(null);
                }
            }
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

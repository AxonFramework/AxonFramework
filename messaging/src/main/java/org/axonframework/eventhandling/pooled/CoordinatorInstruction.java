package org.axonframework.eventhandling.pooled;

import java.util.concurrent.CompletableFuture;

/**
 * An instruction to be consumed by a {@link Coordinator} during its main coordinator task.
 *
 * @author Steven van Beelen
 * @see Coordinator
 * @since 4.5
 */
abstract class CoordinatorInstruction {

    private final CompletableFuture<Boolean> result;

    /**
     * Construct a {@link CoordinatorInstruction}. The given {@code result} will be completed exceptionally or
     * successfully through the {@link #complete(Boolean, Throwable)} method.
     *
     * @param result the {@link CompletableFuture} to complete exceptionally or successfully
     */
    protected CoordinatorInstruction(CompletableFuture<Boolean> result) {
        this.result = result;
    }

    /**
     * Run the task this {@link CoordinatorInstruction} implementation should perform. At the end of this method, {@link
     * #complete(Boolean, Throwable)} should be invoked.
     */
    abstract void run();

    /**
     * Completes this {@link CoordinatorInstruction}'s {@link CompletableFuture}. If the given {@code throwable} is not
     * null, {@link CompletableFuture#completeExceptionally(Throwable)} will be invoked on the {@code result}. Otherwise
     * {@link CompletableFuture#complete(Object)} will be invoked with the given {@code outcome}.
     * <p>
     * This method should be invoked as a follow up of a {@link #run()}.
     *
     * @param outcome   a {@link Boolean} defining whether the {@link #run()} ran successfully yes or no
     * @param throwable a {@link Throwable} defining the error scenario the {@link #run()} ran into
     */
    void complete(Boolean outcome, Throwable throwable) {
        if (throwable != null) {
            result.completeExceptionally(throwable);
        } else {
            result.complete(outcome);
        }
    }

    abstract String description();
}

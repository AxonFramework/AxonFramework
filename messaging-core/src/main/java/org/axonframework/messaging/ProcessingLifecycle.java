package org.axonframework.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ProcessingLifecycle {

    ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action);

    <R> CompletableFuture<R> execute(Function<ProcessingContext, CompletableFuture<R>> action);

    // TODO: Kill or be killed
    <R> Publisher<R> streamingResult(Function<ProcessingContext, ? extends Publisher<R>> action);

    default ProcessingLifecycle runOn(Phase phase, Consumer<ProcessingContext> action) {
        return on(phase, c -> CompletableFuture.runAsync(()-> action.accept(c), Runnable::run));
    }

    ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action);

    enum Phase {

        PREPARE_PROCESSING(true),
        PROCESSING(true),
        AFTER_PROCESSING(true),
        PREPARE_COMMIT(true),
        COMMIT(true),
        AFTER_COMMIT(false),
        ROLLBACK(false),
        COMPLETED(false);

        private final boolean rollbackOnFailure;

        Phase(boolean rollbackOnFailure) {
            this.rollbackOnFailure = rollbackOnFailure;
        }

        public boolean isRollbackOnFailure() {
            return rollbackOnFailure;
        }
    }

}

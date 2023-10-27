package org.axonframework.messaging;

import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reasons to live:
 * 1. support (not provide) transactionality
 * 2. to be a "hook api"
 */
// TODO Does the lifecycle deal with Messages only?!
public interface ProcessingLifecycle {

    ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action);

    /* These methods DO NOT belong on the interface - we don't want users to invoke this
    Can be
    1. null
    2. one
    * */
//    <R> CompletableFuture<R> execute(Function<ProcessingContext, CompletableFuture<R>> action);
    /*
    Can be
    1. null
    2. one
    3. many
    * */
//    <R> Publisher<R> streamingResult(Function<ProcessingContext, ? extends Publisher<R>> action);

    /**
     * TODO next session
     * Drop execute/streamingResult. ProcessingLifecycle isn't aware o the exact result, just that there's a result.
     * The infra component (bus/store/processor) is in charge of checking the result type and acting accordingly.
     * - CommandBus         -> 0 / 1
     * - QueryBus           -> 0 / n / many
     * - EventBus           -> 0
     * - EventProcessor     -> 0
     */
    default ProcessingLifecycle runOn(Phase phase, Consumer<ProcessingContext> action) {
        return on(phase, c -> CompletableFuture.runAsync(()-> action.accept(c), Runnable::run));
    }

    ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action);

    enum Phase {

        // handling stuff...
        PRE_INVOCATION(true),
        INVOCATION(true),
        POST_INVOCATION(true),
        // potentially transactional stuff...
        PREPARE_COMMIT(true),
        COMMIT(true),
        AFTER_COMMIT(false),
        ROLLBACK(false),
        // done, hurray!
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
package org.axonframework.messaging.unitofwork;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reasons to live: 1. support (not provide) transactionality 2. to be a "hook api"
 */
// TODO Does the lifecycle deal with Messages only?!
public interface ProcessingLifecycle {

    ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action);

    default ProcessingLifecycle onPreInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(Phase.PRE_INVOCATION, action);
    }

    default ProcessingLifecycle onInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(Phase.INVOCATION, action);
    }

    default ProcessingLifecycle onPostInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(Phase.POST_INVOCATION, action);
    }

    default ProcessingLifecycle onPrepareCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(Phase.PREPARE_COMMIT, action);
    }

    default ProcessingLifecycle onCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(Phase.COMMIT, action);
    }

    default ProcessingLifecycle onAfterCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(Phase.AFTER_COMMIT, action);
    }

    default ProcessingLifecycle onRollback(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(Phase.ROLLBACK, action);
    }

    default ProcessingLifecycle onCompleted(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(Phase.COMPLETED, action);
    }

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
        return on(phase, c -> CompletableFuture.runAsync(() -> action.accept(c), Runnable::run));
    }

    /**
     * Register the given {@code action} to be invoked once the {@link Phase#COMPLETED completed phase} is reached. The
     * given {@code action} will be invoked immediately if this {@code ProcessingLifecycle} already reached the
     * {@link Phase#COMPLETED completed phase}.
     *
     * @param action The action to perform once the {@link Phase#COMPLETED completed phase} is reached or has already
     *               been reached.
     * @return The current {@link ProcessingLifecycle}, for chaining.
     */
    ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action);

    // TODO add interface with order, so that users can define their own phases among our fixed phases
    // TODO make special case out of rollback/onError with its own order
    // TODO make special case out of completed with a dedicated method and no phase instance
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
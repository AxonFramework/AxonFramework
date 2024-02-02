package org.axonframework.messaging.unitofwork;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * TODO Add/enhance documentation as described in #2966.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savić
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 */
public interface ProcessingLifecycle {

    boolean isStarted();

    boolean isError();

    boolean isCommitted();

    boolean isCompleted();

    ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action);

    default ProcessingLifecycle runOn(Phase phase, Consumer<ProcessingContext> action) {
        return on(phase, c -> {
            action.accept(c);
            return CompletableFuture.completedFuture(null);
        });
    }

    default ProcessingLifecycle onPreInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(DefaultPhases.PRE_INVOCATION, action);
    }

    default ProcessingLifecycle runOnPreInvocation(Consumer<ProcessingContext> action) {
        return runOn(DefaultPhases.PRE_INVOCATION, action);
    }

    default ProcessingLifecycle onInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(DefaultPhases.INVOCATION, action);
    }

    default ProcessingLifecycle runOnInvocation(Consumer<ProcessingContext> action) {
        return runOn(DefaultPhases.INVOCATION, action);
    }

    default ProcessingLifecycle onPostInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(DefaultPhases.POST_INVOCATION, action);
    }

    default ProcessingLifecycle runOnPostInvocation(Consumer<ProcessingContext> action) {
        return runOn(DefaultPhases.POST_INVOCATION, action);
    }

    default ProcessingLifecycle onPrepareCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(DefaultPhases.PREPARE_COMMIT, action);
    }

    default ProcessingLifecycle runOnPrepareCommit(Consumer<ProcessingContext> action) {
        return runOn(DefaultPhases.PREPARE_COMMIT, action);
    }

    default ProcessingLifecycle onCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(DefaultPhases.COMMIT, action);
    }

    default ProcessingLifecycle runOnCommit(Consumer<ProcessingContext> action) {
        return runOn(DefaultPhases.COMMIT, action);
    }

    default ProcessingLifecycle onAfterCommit(Function<ProcessingContext, CompletableFuture<?>> action) {
        return on(DefaultPhases.AFTER_COMMIT, action);
    }

    default ProcessingLifecycle runOnAfterCommit(Consumer<ProcessingContext> action) {
        return runOn(DefaultPhases.AFTER_COMMIT, action);
    }

    ProcessingLifecycle onError(ErrorHandler action);

    ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action);

    default ProcessingLifecycle doFinally(Consumer<ProcessingContext> action) {
        onError((c, p, e) -> action.accept(c));
        whenComplete(action);
        return this;
    }

    /**
     * Interface describing a component that gets notified when an error is detected within a Processing Context
     */
    @FunctionalInterface
    interface ErrorHandler {

        /**
         * Invoked when an error is detected in a Processing Context and its lifecycle has been aborted. The state of
         * the lifecycle will always return {@code true} for {@link ProcessingContext#isError()} and
         * {@link ProcessingContext#isCompleted()}.
         *
         * @param processingContext The context in which the error occurred
         * @param phase             The phase used to register the handler which caused the ProcessingContext to fail
         * @param error             The exception or error describing the cause
         */
        void handle(ProcessingContext processingContext, Phase phase, Throwable error);
    }

    /**
     * Interface describing a possible Phase for a ProcessingLifecycle. Lifecycle handlers are invoked in the order of
     * their respective phase, where handlers in phases with the same order may be invoked in parallel.
     */
    interface Phase {

        /**
         * The order of this phase compared to other phases. Phases with the same order are considered "simultaneous"
         * and may have their handlers invoked in parallel.
         *
         * @return the int describing the relative order of this phase
         */
        int order();

        default boolean isBefore(Phase other) {
            return this.order() < other.order();
        }

        default boolean isAfter(Phase other) {
            return this.order() > other.order();
        }
    }

    /**
     * Default phases used for the shorthand methods in the ProcessingLifecycle
     */
    enum DefaultPhases implements Phase {

        PRE_INVOCATION(-10000),
        INVOCATION(0),
        POST_INVOCATION(10000),
        PREPARE_COMMIT(20000),
        COMMIT(30000),
        AFTER_COMMIT(40000);

        private final int order;

        DefaultPhases(int order) {
            this.order = order;
        }

        @Override
        public int order() {
            return order;
        }
    }
}
package org.axonframework.messaging.unitofwork;

import org.axonframework.common.FutureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The UnitOfWork is a hook to phase tasks
 * TODO rename
 */
public class AsyncUnitOfWork implements ProcessingLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(AsyncUnitOfWork.class);

    private final String identifier;
    private final UnitOfWorkProcessingContext context;

    public AsyncUnitOfWork() {
        this(UUID.randomUUID().toString());
    }

    public AsyncUnitOfWork(String identifier) {
        this(identifier, Runnable::run);
    }

    public AsyncUnitOfWork(String identifier, Executor workScheduler) {
        this.identifier = identifier;
        this.context = new UnitOfWorkProcessingContext(identifier, workScheduler);
    }

    @Override
    public String toString() {
        return "AsyncUnitOfWork{" +
                "id='" + identifier + '\'' +
                "phase='" + context.currentPhase.get() + '\'' +
                '}';
    }

    @Override
    public AsyncUnitOfWork on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
        context.on(phase, action);
        return this;
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        return context.whenComplete(action);
    }

    /**
     * Executes all the registered handlers in their respective phases.
     *
     * @return a {@link CompletableFuture} that returns normally when the Unit Of Work has been committed or
     * exceptionally with the exception that caused the Unit of Work to have been rolled back.
     */
    public CompletableFuture<Void> execute() {
        return context.commit();
    }

    /**
     * Registers the given invocation for the {@link Phase#INVOCATION Invocation Phase} and executes the Unit of Work.
     * The return value of the invocation is returned when this Unit of Work is committed.
     *
     * @param invocation The handler to execute in the {@link Phase#INVOCATION Invocation Phase}
     * @param <R>        The type of return value returned by the invocation
     * @return a CompletableFuture that returns normally with the return value of the invocation when the Unit Of Work
     * has been committed or exceptionally with the exception that caused the Unit of Work to have been rolled back.
     */
    public <R> CompletableFuture<R> executeWithResult(Function<ProcessingContext, CompletableFuture<R>> invocation) {
        CompletableFuture<R> result = new CompletableFuture<>();
        on(Phase.INVOCATION, p -> invocation.apply(p).whenComplete((r, e) -> {
            if (e == null) {
                result.complete(r);
            } else {
                result.completeExceptionally(e);
            }
        }));
        return execute().thenCombine(result, (executeResult, invocationResult) -> invocationResult);
    }

    private static class UnitOfWorkProcessingContext implements ProcessingContext {

        private final ConcurrentHashMap<Phase, Queue<Function<ProcessingContext, CompletableFuture<?>>>> phaseHandlers = new ConcurrentHashMap<>();
        private final AtomicReference<Phase> currentPhase = new AtomicReference<>(null);
        private final LocalResources resources = new LocalResources();
        private final EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
        private final Queue<Consumer<ProcessingContext>> afterCompleteHandlers = new ConcurrentLinkedQueue<>();
        private final String name;
        private final Executor workScheduler;

        public UnitOfWorkProcessingContext(String name, Executor workScheduler) {
            this.name = name;
            this.workScheduler = workScheduler;
            phaseHandlers.computeIfAbsent(Phase.COMPLETED, k -> new ConcurrentLinkedQueue<>())
                         .add(safe(Phase.COMPLETED, this::complete));
        }

        private CompletableFuture<?> complete(ProcessingContext context) {
            return CompletableFuture.runAsync(() -> {
                while (!afterCompleteHandlers.isEmpty()) {
                    Consumer<ProcessingContext> next = afterCompleteHandlers.poll();
                    if (next != null) {
                        next.accept(context);
                    }
                }
            }, workScheduler);
        }

        @Override
        public Resources resources() {
            return resources;
        }

        @Override
        public boolean isStarted() {
            return phases.contains(Phase.INVOCATION);
        }

        @Override
        public boolean isRolledBack() {
            return phases.contains(Phase.ROLLBACK);
        }

        @Override
        public boolean isCommitted() {
            return phases.contains(Phase.AFTER_COMMIT);
        }

        @Override
        public boolean isCompleted() {
            return phases.contains(Phase.COMPLETED);
        }

        @Override
        public ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
            var p = currentPhase.get();
            if (p != null && phase.ordinal() <= p.ordinal()) {
                throw new IllegalStateException("ProcessingContext is already in " + phase.name() + " phase");
            }
            phaseHandlers.computeIfAbsent(phase, k -> new ConcurrentLinkedQueue<>())
                         .add(safe(phase, action));
            return this;
        }

        @Override
        public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
            this.afterCompleteHandlers.add(action);
            var p = currentPhase.get();
            if (p != null && Phase.COMPLETED.ordinal() <= p.ordinal()
                    && afterCompleteHandlers.remove(action)) {
                // when in the completed phase, execute immediately
                // the removal attempt is to make sure that we aren't concurrently executing from the registering thread
                // as well as the thread that completed the processing context.
                try {
                    action.accept(this);
                } catch (Exception e) {
                    logger.debug("A phase handler threw an exception", e);
                }
            }
            return this;
        }

        private Function<ProcessingContext, CompletableFuture<?>> safe(
                Phase phase,
                Function<ProcessingContext, CompletableFuture<?>> action) {
            return c -> action.apply(c)
                              .whenComplete((r, e) -> logger.debug(
                                      "A handler threw an exception in phase {}", phase, e
                              ));
        }

        public CompletableFuture<Void> commit() {
            if (currentPhase.get() != null) {
                throw new IllegalStateException("ProcessingContext cannot be committed (again)");
            }
            return runPhase(Phase.PRE_INVOCATION)
                    .thenCompose(r -> runPhase(Phase.INVOCATION))
                    .thenCompose(r -> runPhase(Phase.POST_INVOCATION))
                    .thenCompose(r -> runPhase(Phase.PREPARE_COMMIT))
                    .thenCompose(r -> runPhase(Phase.COMMIT))
                    .exceptionallyCompose(e -> runPhase(Phase.ROLLBACK)
                            // always continue with the original error
                            .thenCompose(r -> CompletableFuture.failedFuture(e)))
                    .thenCompose(r -> runPhase(Phase.AFTER_COMMIT))
                    .thenCompose(r -> runPhase(Phase.COMPLETED))
                    .exceptionallyCompose(e -> runPhase(Phase.COMPLETED)
                            // always continue with the original error
                            .thenCompose(r -> CompletableFuture.failedFuture(e)));
        }

        private CompletableFuture<Void> runPhase(Phase phase) {
            phases.add(phase);
            currentPhase.set(phase);

            Queue<Function<ProcessingContext, CompletableFuture<?>>> handlers =
                    phaseHandlers.getOrDefault(phase, new LinkedList<>());
            if (phase == Phase.COMPLETED) {
                handlers.add(afterCompletion());
            }

            CompletableFuture<Void> result =
                    handlers.stream()
                            .map(handler -> CompletableFuture.completedFuture(null)
                                                             .thenComposeAsync(r -> handler.apply(this), workScheduler)
                                                             .thenAccept(FutureUtils::ignoreResult))
                            .reduce(CompletableFuture::allOf)
                            .orElseGet(FutureUtils::emptyCompletedFuture);

            return result.exceptionallyCompose(e -> {
                if (phase.isRollbackOnFailure()) {
                    return CompletableFuture.failedFuture(e);
                }
                return FutureUtils.emptyCompletedFuture();
            });
        }

        private Function<ProcessingContext, CompletableFuture<?>> afterCompletion() {
            return c -> {
                try {
                    while (!afterCompleteHandlers.isEmpty()) {
                        afterCompleteHandlers.poll().accept(c);
                    }
                    return CompletableFuture.completedFuture(null);
                } catch (Throwable e) {
                    return CompletableFuture.failedFuture(e);
                }
            };
        }

        @Override
        public String toString() {
            return "UnitOfWorkProcessingContext{" +
                    "name='" + name + '\'' +
                    ", currentPhase=" + currentPhase.get() +
                    '}';
        }
    }
}

package org.axonframework.messaging.unitofwork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The UnitOfWork is a hook to phase tasks
 * TODO rename
 */
public class AsyncUnitOfWork implements ProcessingLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(UnitOfWork.class);

    private final String name;
    private final UnitOfWorkProcessingContext context;

    public AsyncUnitOfWork() {
        this(UUID.randomUUID().toString());
    }

    public AsyncUnitOfWork(String name) {
        this(name, Runnable::run);
    }

    public AsyncUnitOfWork(String name, Executor workScheduler) {
        this(name, null, workScheduler);
    }

    public AsyncUnitOfWork(String name, ProcessingContext parent, Executor workScheduler) {
        this.name = name;
        this.context = new UnitOfWorkProcessingContext(name, parent, workScheduler);
    }

    public static <R> CompletableFuture<R> createAndExecute(Function<ProcessingContext, CompletableFuture<R>> action) {
        return new AsyncUnitOfWork().execute(action);
    }

    @Override
    public String toString() {
        return "AsyncUnitOfWork{" +
                "name='" + name + '\'' +
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
     * @return a {@link CompletableFuture} that returns normally when the Unit Of Work has been committed or exceptionally with
     * the exception that caused the Unit of Work to have been rolled back.
     */
    public CompletableFuture<Void> execute() {
        return context.commit();
    }

    public ProcessingContext processingContext() {
        return context;
    }

    private static class UnitOfWorkProcessingContext implements ProcessingContext {

        private static final ThreadLocal<ProcessingContext> currentProcessingContext = new ThreadLocal<>();

        private final ConcurrentHashMap<Phase, Queue<Function<ProcessingContext, CompletableFuture<?>>>> phaseHandlers = new ConcurrentHashMap<>();
        private final AtomicReference<Phase> currentPhase = new AtomicReference<>(null);
        private final AtomicBoolean isRoot = new AtomicBoolean();
        private final AtomicReference<ProcessingContext> parentProcessingContext = new AtomicReference<>();
        private final LocalResources resources = new LocalResources();
        private final EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
        private final Queue<Consumer<ProcessingContext>> afterCompleteHandlers = new ConcurrentLinkedQueue<>();
        private final String name;
        private final Executor workScheduler;

        public UnitOfWorkProcessingContext(String name, ProcessingContext parent, Executor workScheduler) {
            this.name = name;
            this.workScheduler = workScheduler;
            if (parent != null) {
                this.parentProcessingContext.set(parent);
            }
            phaseHandlers.computeIfAbsent(Phase.COMPLETED, k -> new ConcurrentLinkedQueue<>())
                         .add(safe(Phase.COMPLETED, withContext(this::complete)));
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

        private <T, R> Function<T, R> withContext(Function<T, R> function) {
            return t -> {
                var previous = currentProcessingContext.get();
                currentProcessingContext.set(this);
                try {
                    return function.apply(t);
                } finally {
                    if (previous == null) {
                        currentProcessingContext.remove();
                    } else {
                        currentProcessingContext.set(previous);
                    }
                }
            };
        }

        private <T> Consumer<T> withContext(Consumer<T> callable) {
            return t -> {
                var previous = currentProcessingContext.get();
                currentProcessingContext.set(this);
                try {
                    callable.accept(t);
                } finally {
                    if (previous == null) {
                        currentProcessingContext.remove();
                    } else {
                        currentProcessingContext.set(previous);
                    }
                }
            };
        }

        @Override
        public Resources resources(ResourceScope scope) {
            if (isRoot.get()) {
                return resources;
            } else {
                var parent = parentProcessingContext.get();
                if (parent == null) {
                    throw new IllegalStateException("Resources can only be accessed in lifecycle methods");
                }
                return switch (scope) {
                    case LOCAL -> resources;
                    case INHERITED -> new InheritableResources(resources, parent.resources(scope));
                    case SHARED -> parent.resources(scope);
                };
            }
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
                         .add(safe(phase, withContext(action)));
            return this;
        }

        @Override
        public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
            var contextualAction = withContext(action);
            this.afterCompleteHandlers.add(contextualAction);
            var p = currentPhase.get();
            if (p != null && Phase.COMPLETED.ordinal() <= p.ordinal()
                    && afterCompleteHandlers.remove(contextualAction)) {
                // when in the completed phase, execute immediately
                // the removal attempt is to make sure that we aren't concurrently  executing from the registering thread
                // as well as the thread that switched the phase to completed.
                try {
                    contextualAction.accept(this);
                } catch (Exception e) {
                    logger.debug("A phase handler threw an exception", e);
                }
            }
            return this;
        }

        private Function<ProcessingContext, CompletableFuture<?>> safe(
                Phase phase,
                Function<ProcessingContext, CompletableFuture<?>> action
        ) {
            return c -> action.apply(c)
                              .whenComplete((r, e) -> logger.debug(
                                      "A handler threw an exception in phase {}", phase, e
                              ));
        }

        public CompletableFuture<Void> commit() {
            if (currentPhase.get() != null) {
                throw new IllegalStateException("ProcessingContext cannot be committed (again)");
            }
            return resolveParent().map(this::commitNested)
                                  .orElseGet(this::commitNormal);
        }

        private Optional<ProcessingContext> resolveParent() {
            var registeredParent = Optional.ofNullable(parentProcessingContext.get());
            var parentFromContext = Optional.ofNullable(currentProcessingContext.get());
            if (parentFromContext.isPresent() && registeredParent.isPresent()) {
                if (!Objects.equals(parentFromContext.get(), registeredParent.get())) {
                    throw new IllegalStateException(
                            "Inconsistent registration of Unit of Work nesting. "
                                    + "Registered parent does not match parent from subscriber context."
                    );
                }
            } else if (parentFromContext.isPresent()) {
                return parentFromContext;
            }
            return registeredParent;
        }

        private CompletableFuture<Void> commitNested(ProcessingContext parent) {
            parentProcessingContext.set(parent);
            return innerCommit()
                    .thenAccept(r -> {
                        parent.on(Phase.AFTER_COMMIT, p -> runPhase(Phase.AFTER_COMMIT));
                        parent.on(Phase.ROLLBACK, p -> runPhase(Phase.ROLLBACK));
                        parent.on(Phase.COMPLETED, p -> runPhase(Phase.COMPLETED));
                    })
                    .exceptionallyCompose(e -> {
                        parent.on(Phase.COMPLETED, p -> runPhase(Phase.COMPLETED));
                        return CompletableFuture.failedFuture(e);
                    });
        }

        private CompletableFuture<Void> commitNormal() {
            isRoot.set(true);
            return innerCommit()
                    .thenCompose(r -> runPhase(Phase.AFTER_COMMIT))
                    .thenCompose(r -> runPhase(Phase.COMPLETED))
                    .exceptionallyCompose(e -> runPhase(Phase.COMPLETED)
                            // always continue with the original error
                            .thenCompose(r -> CompletableFuture.failedFuture(e)));
        }

        private CompletableFuture<Void> innerCommit() {
            return runPhase(Phase.PRE_INVOCATION)
                    .thenCompose(r -> runPhase(Phase.INVOCATION))
                    .thenCompose(r -> runPhase(Phase.POST_INVOCATION))
                    .thenCompose(r -> runPhase(Phase.PREPARE_COMMIT))
                    .thenCompose(r -> runPhase(Phase.COMMIT))
                    .exceptionallyCompose(e -> runPhase(Phase.ROLLBACK)
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
                                                             .thenAccept(c -> {
                                                                 // TODO?
                                                             }))
                            .reduce(CompletableFuture::allOf)
                            .orElse(CompletableFuture.completedFuture(null));

            return result.exceptionallyCompose(e -> {
                if (phase.isRollbackOnFailure()) {
                    return CompletableFuture.failedFuture(e);
                }
                return CompletableFuture.completedFuture(null);
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

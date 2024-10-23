/*
 * Copyright (c) 2010-2024. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.unitofwork;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Context;
import org.axonframework.common.FutureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * This class represents a Unit of Work that monitors the processing of a task.
 * <p/>
 * As an implementation of the {@link ProcessingLifecycle}, steps can be attached in several
 * {@link ProcessingContext.DefaultPhases phases} of the Unit of Work to ensure the task-to-process is taken care off
 * correctly. Furthermore, the Unit of Work implements resource management through the {@link ProcessingContext},
 * providing the possibility to carry along resources throughout the phases.
 * <p/>
 * It is strongly recommended to interface with the {@code ProcessingLifecycle} and/or {@code ProcessingContext} instead
 * of with the {@link AsyncUnitOfWork} directly.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savić
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 0.6
 */
// TODO #3064 - Rename to UnitOfWork once old version is removed.
public class AsyncUnitOfWork implements ProcessingLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(AsyncUnitOfWork.class);

    private final String identifier;
    private final UnitOfWorkProcessingContext context;


    /**
     * Constructs a {@link AsyncUnitOfWork} with a {@link UUID#randomUUID() random UUID String}. Will execute provided
     * actions on the same thread invoking this Unit of Work.
     */
    public AsyncUnitOfWork() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Constructs a {@link AsyncUnitOfWork} with the given {@code identifier}. Will execute provided actions on the same
     * thread invoking this Unit of Work.
     *
     * @param identifier The identifier of this Unit of Work.
     */
    public AsyncUnitOfWork(String identifier) {
        this(identifier, Runnable::run);
    }

    /**
     * Constructs a {@link AsyncUnitOfWork} with the given {@code identifier}, processing actions through the given
     * {@code workScheduler}.
     *
     * @param identifier    The identifier of this Unit of Work.
     * @param workScheduler The {@link Executor} used to process the steps attached to the phases in this Unit of Work
     */
    public AsyncUnitOfWork(String identifier, Executor workScheduler) {
        this.identifier = identifier;
        this.context = new UnitOfWorkProcessingContext(identifier, workScheduler);
    }

    @Override
    public boolean isStarted() {
        return context.isStarted();
    }

    @Override
    public boolean isError() {
        return context.isError();
    }

    @Override
    public boolean isCommitted() {
        return context.isCommitted();
    }

    @Override
    public boolean isCompleted() {
        return context.isCompleted();
    }

    @Override
    public AsyncUnitOfWork on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
        context.on(phase, action);
        return this;
    }

    @Override
    public ProcessingLifecycle onError(ErrorHandler action) {
        return context.onError(action);
    }

    @Override
    public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
        return context.whenComplete(action);
    }

    /**
     * Executes all the registered action in their respective
     * {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle.Phase phases}.
     *
     * @return A {@link CompletableFuture} that returns normally when this Unit Of Work has been committed or
     * exceptionally with the exception that caused the Unit of Work to fail.
     */
    public CompletableFuture<Void> execute() {
        return context.commit();
    }

    /**
     * Registers the given {@code action} for the {@link DefaultPhases#INVOCATION invocation Phase} and executes this
     * Unit of Work right away.
     * <p>
     * The return value of the given {@code action} is returned when this Unit of Work is committed, disregarding
     * intermittent results of actions registered in other
     * {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle.Phase phases}.
     *
     * @param action The {@link Function} that's given the active {@link ProcessingContext} and returns a
     *               {@link CompletableFuture} for chaining purposes and to carry the action's result.
     * @param <R>    The type of return value returned by the {@code action}.
     * @return A {@link CompletableFuture} that returns normally with the return value of the given {@code action} when
     * the Unit Of Work has been committed. Or, an exceptionally completed future with the exception that caused this
     * Unit of Work to fail.
     */
    public <R> CompletableFuture<R> executeWithResult(Function<ProcessingContext, CompletableFuture<R>> action) {
        CompletableFuture<R> result = new CompletableFuture<>();
        onInvocation(processingContext -> safe(() -> action.apply(processingContext))
                .whenComplete(FutureUtils.alsoComplete(result)));
        return execute().thenCombine(result, (executeResult, invocationResult) -> invocationResult);
    }

    /**
     * Wraps a given {@code action} in a try-catch block to ensure exceptions are exclusively returned as a failed
     * {@link CompletableFuture}.
     *
     * @param action A {@link Callable} to execute within the try-catch block.
     * @return A {@link CompletableFuture} wrapping both the successful and exceptional result of the given
     * {@code action}.
     */
    private <R> CompletableFuture<R> safe(Callable<CompletableFuture<R>> action) {
        try {
            return action.call();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public String toString() {
        return "UnitOfWork{" + "identifier='" + identifier + '\'' + "phase='" + context.currentPhase.get() + '\'' + '}';
    }

    private static class UnitOfWorkProcessingContext implements ProcessingContext {

        private final AtomicReference<Status> status = new AtomicReference<>(Status.NOT_STARTED);
        private final AtomicReference<Phase> currentPhase = new AtomicReference<>(null);

        private final ConcurrentNavigableMap<Phase, Queue<Function<ProcessingContext, CompletableFuture<?>>>> phaseActions =
                new ConcurrentSkipListMap<>(Comparator.comparingInt(Phase::order));
        private final Queue<Consumer<ProcessingContext>> completeHandlers = new ConcurrentLinkedQueue<>();
        private final Queue<ErrorHandler> errorHandlers = new ConcurrentLinkedQueue<>();
        private final AtomicReference<CauseAndPhase> errorCause = new AtomicReference<>();

        private final String identifier;
        private final Executor workScheduler;
        private final ConcurrentMap<ResourceKey<?>, Object> resources;

        private UnitOfWorkProcessingContext(String identifier, Executor workScheduler) {
            this.identifier = identifier;
            this.workScheduler = workScheduler;
            this.resources = new ConcurrentHashMap<>();
        }

        @Override
        public boolean isStarted() {
            return status.get() != Status.NOT_STARTED;
        }

        @Override
        public boolean isError() {
            return status.get() == Status.COMPLETED_ERROR;
        }

        @Override
        public boolean isCommitted() {
            return status.get() == Status.COMPLETED;
        }

        @Override
        public boolean isCompleted() {
            Status currentStatus = status.get();
            return currentStatus == Status.COMPLETED || currentStatus == Status.COMPLETED_ERROR;
        }

        @Override
        public ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
            var current = currentPhase.get();
            if (current != null && phase.order() <= current.order()) {
                throw new IllegalStateException(
                        "Failed to register handler in phase " + phase + " (" + phase.order() + "). "
                                + "ProcessingContext is already in phase " + current + " (" + current.order() + ")."
                );
            }
            phaseActions.computeIfAbsent(phase, p -> new ConcurrentLinkedQueue<>())
                        .add(safe(phase, action));
            return this;
        }

        /**
         * Wraps a given {@code action}, that is to be executed in the given {@code phase}, in a try-catch block to
         * ensure exceptions are exclusively returned as a failed {@link CompletableFuture}.
         *
         * @param phase  The original phase instance the handler is registered under
         * @param action The {@link Function} to perform safely. It's given the active {@link ProcessingContext} and
         *               returns a {@link CompletableFuture} for chaining purposes and to carry the action's result.
         * @return A {@link CompletableFuture} wrapping both the successful and exceptional result of the given
         * {@code action}.
         */
        private Function<ProcessingContext, CompletableFuture<?>> safe(
                Phase phase, Function<ProcessingContext, CompletableFuture<?>> action
        ) {
            return processingContext -> {
                CompletableFuture<?> result;
                try {
                    result = action.apply(processingContext);
                } catch (Exception e) {
                    result = CompletableFuture.failedFuture(e);
                }

                return result.exceptionallyCompose(e -> {
                    if (!errorCause.compareAndSet(null, new CauseAndPhase(phase, e))) {
                        errorCause.get().cause().addSuppressed(e);
                    }
                    return CompletableFuture.failedFuture(e);
                });
            };
        }

        @Override
        public ProcessingLifecycle onError(ErrorHandler action) {
            ErrorHandler silentAction = failSilently(action);
            this.errorHandlers.add(silentAction);
            var currentStatus = status.get();

            if (currentStatus == Status.COMPLETED_ERROR && errorHandlers.remove(silentAction)) {
                // When in the COMPLETED_ERROR status, execute immediately.
                // The removal attempt is to make sure that we aren't concurrently executing from the registering thread
                // as well as the thread that completed the processing context.
                CauseAndPhase causeAndPhase = errorCause.get();
                silentAction.handle(this, causeAndPhase.phase(), causeAndPhase.cause());
            }
            return this;
        }

        private ErrorHandler failSilently(ErrorHandler action) {
            return (context, phase, exception) -> {
                try {
                    action.handle(context, phase, exception);
                } catch (Exception e) {
                    logger.warn("An onError handler threw an exception.", e);
                }
            };
        }

        @Override
        public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
            Consumer<ProcessingContext> silentAction = completeSilently(action);
            this.completeHandlers.add(silentAction);
            var currentStatus = status.get();

            if (currentStatus == Status.COMPLETED && completeHandlers.remove(silentAction)) {
                // When in the COMPLETED status, execute immediately.
                // The removal attempt is to make sure that we aren't concurrently executing from the registering thread
                // as well as the thread that completed the processing context.
                silentAction.accept(this);
            }
            return this;
        }

        private Consumer<ProcessingContext> completeSilently(Consumer<ProcessingContext> action) {
            return processingContext -> {
                try {
                    action.accept(processingContext);
                } catch (Exception e) {
                    logger.warn("A Completion handler threw an exception.", e);
                }
            };
        }

        private CompletableFuture<Void> commit() {
            if (!status.compareAndSet(Status.NOT_STARTED, Status.STARTED)) {
                throw new IllegalStateException(
                        "Cannot switch [" + status.get() + "] to STARTED. "
                                + "This ProcessingContext cannot be committed (again)."
                );
            }

            return executeAllPhaseHandlers()
                    .thenRun(this::runCompletionHandlers)
                    .exceptionallyCompose(this::runErrorHandlers);
        }

        private CompletableFuture<Void> executeAllPhaseHandlers() {
            if (phaseActions.isEmpty()) {
                // We're done.
                return FutureUtils.emptyCompletedFuture();
            }

            CompletableFuture<Void> nextPhaseResult = runNextPhase().toCompletableFuture();
            // Avoid stack overflow due to recursion when executed in single thread.
            while (!phaseActions.isEmpty() && nextPhaseResult.isDone()) {
                if (nextPhaseResult.isCompletedExceptionally()) {
                    return nextPhaseResult;
                } else {
                    nextPhaseResult = runNextPhase().toCompletableFuture();
                }
            }
            return nextPhaseResult.thenCompose(result -> executeAllPhaseHandlers());
        }

        private void runCompletionHandlers() {
            status.set(Status.COMPLETED);

            while (!completeHandlers.isEmpty()) {
                Consumer<ProcessingContext> nextCompletionHandler = completeHandlers.poll();
                if (nextCompletionHandler != null) {
                    workScheduler.execute(() -> nextCompletionHandler.accept(this));
                }
            }
        }

        private CompletionStage<Void> runErrorHandlers(Throwable e) {
            status.set(Status.COMPLETED_ERROR);
            CauseAndPhase recordedCause = errorCause.get();

            while (!errorHandlers.isEmpty()) {
                ErrorHandler nextErrorHandler = errorHandlers.poll();
                if (nextErrorHandler != null) {
                    workScheduler.execute(
                            () -> nextErrorHandler.handle(this, recordedCause.phase(), recordedCause.cause())
                    );
                }
            }
            return CompletableFuture.failedFuture(e);
        }

        private CompletableFuture<Void> runNextPhase() {
            if (phaseActions.isEmpty()) {
                return FutureUtils.emptyCompletedFuture();
            }
            Phase current = phaseActions.firstKey();
            currentPhase.set(current);

            Queue<Function<ProcessingContext, CompletableFuture<?>>> actionQueue = phaseActions.remove(current);
            if (actionQueue == null || actionQueue.isEmpty()) {
                logger.debug("Skipping phase {} (with order [{}]), since no actions are registered.",
                             current, current.order());
                return FutureUtils.emptyCompletedFuture();
            }
            logger.debug("Calling {}# actions in phase {} (with order {}).",
                         actionQueue.size(), current, current.order());

            return actionQueue.stream()
                              .map(handler -> FutureUtils.emptyCompletedFuture()
                                                         .thenComposeAsync(result -> handler.apply(this), workScheduler)
                                                         .thenAccept(FutureUtils::ignoreResult))
                              .reduce(CompletableFuture::allOf)
                              .orElseGet(FutureUtils::emptyCompletedFuture);
        }

        @Override
        public boolean containsResource(@Nonnull ResourceKey<?> key) {
            return resources.containsKey(key);
        }

        @Override
        public <T> T getResource(@Nonnull ResourceKey<T> key) {
            //noinspection unchecked
            return (T) resources.get(key);
        }

        @Override
        public <T> Context withResource(@Nonnull ResourceKey<T> key,
                                        @Nonnull T resource) {
            return branchedWithResource(key, resource);
        }

        @Override
        public void putAll(@Nonnull Context context) {
            this.context.putAll(context);
        }

        @Override
        public <T> T putResource(@Nonnull ResourceKey<T> key,
                                 @Nonnull T resource) {
            //noinspection unchecked
            return (T) resources.put(key, resource);
        }

        @Override
        public <T> T updateResource(@Nonnull ResourceKey<T> key,
                                    @Nonnull UnaryOperator<T> resourceUpdater) {
            //noinspection unchecked
            return (T) resources.compute(key, (k, v) -> resourceUpdater.apply((T) v));
        }

        @Override
        public <T> T putResourceIfAbsent(@Nonnull ResourceKey<T> key,
                                         @Nonnull T resource) {
            //noinspection unchecked
            return (T) resources.putIfAbsent(key, resource);
        }

        @Override
        public <T> T computeResourceIfAbsent(@Nonnull ResourceKey<T> key,
                                             @Nonnull Supplier<T> resourceSupplier) {
            //noinspection unchecked
            return (T) resources.computeIfAbsent(key, t -> resourceSupplier.get());
        }

        @Override
        public <T> T removeResource(@Nonnull ResourceKey<T> key) {
            //noinspection unchecked
            return (T) resources.remove(key);
        }

        @Override
        public <T> boolean removeResource(@Nonnull ResourceKey<T> key,
                                          @Nonnull T expectedResource) {
            return resources.remove(key, expectedResource);
        }

        @Override
        public Map<ResourceKey<?>, ?> asMap() {
            return this.context.asMap();
        }

        @Override
        public String toString() {
            return "UnitOfWorkProcessingContext{"
                    + "identifier='" + identifier + '\'' + ", currentPhase=" + currentPhase.get()
                    + '}';
        }

        private enum Status {
            NOT_STARTED, STARTED, COMPLETED_ERROR, COMPLETED
        }

        /**
         * Tuple combining the given {@code phase} and {@code cause} to be used during the invocation of registered
         * {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle.ErrorHandler ErrorHandlers}.
         *
         * @param phase The {@link org.axonframework.messaging.unitofwork.ProcessingLifecycle.Phase} in which the given
         *              {@code cause} was thrown.
         * @param cause The {@link Throwable} thrown in an action executed in the given {@code phase}.
         */
        private record CauseAndPhase(Phase phase, Throwable cause) {

        }
    }
}

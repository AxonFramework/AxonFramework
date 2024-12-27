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

package org.axonframework.deadline.dbscheduler;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.SchedulerState;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.TaskWithDataDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.AbstractDeadlineManager;
import org.axonframework.deadline.DeadlineException;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineManagerSpanFactory;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.DefaultDeadlineManagerSpanFactory;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.jobrunr.DeadlineDetails;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerBinaryEventData;
import org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerEventScheduler;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.ClassBasedMessageNameResolver;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageNameResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanScope;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.deadline.dbscheduler.DbSchedulerDeadlineToken.TASK_NAME;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of {@link DeadlineManager} that delegates scheduling and triggering to a db scheduler
 * {@link Scheduler}.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
@SuppressWarnings("Duplicates")
public class DbSchedulerDeadlineManager extends AbstractDeadlineManager implements Lifecycle {

    private static final Logger logger = getLogger(DbSchedulerDeadlineManager.class);
    private static final TaskWithDataDescriptor<DbSchedulerBinaryDeadlineDetails> binaryTaskDescriptor =
            new TaskWithDataDescriptor<>(TASK_NAME, DbSchedulerBinaryDeadlineDetails.class);
    private static final TaskWithDataDescriptor<DbSchedulerHumanReadableDeadlineDetails> humanReadableTaskDescriptor =
            new TaskWithDataDescriptor<>(TASK_NAME, DbSchedulerHumanReadableDeadlineDetails.class);

    private final ScopeAwareProvider scopeAwareProvider;
    private final Scheduler scheduler;
    private final Serializer serializer;
    private final TransactionManager transactionManager;
    private final DeadlineManagerSpanFactory spanFactory;
    private final boolean useBinaryPojo;
    private final boolean startScheduler;
    private final boolean stopScheduler;
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Instantiate a Builder to be able to create a {@link DbSchedulerDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link DeadlineManagerSpanFactory} is defaulted to a {@link DefaultDeadlineManagerSpanFactory} backed by a {@link NoOpSpanFactory}.
     * <p>
     * The {@code useBinaryPojo} and {@code startScheduler} are defaulted to {@code true}.
     * <p>
     * The {@link Scheduler}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard requirements</b> and as such
     * should be provided.
     *
     * @return a Builder to be able to create a {@link DbSchedulerDeadlineManager}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link DbSchedulerDeadlineManager} based on the fields contained in the
     * {@link DbSchedulerDeadlineManager.Builder}.
     * <p>
     * Will assert that the {@link ScopeAwareProvider}, {@link Scheduler} and {@link Serializer} are not {@code null},
     * and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link DbSchedulerDeadlineManager.Builder} used to instantiate a
     *                {@link DbSchedulerDeadlineManager} instance
     */
    protected DbSchedulerDeadlineManager(Builder builder) {
        builder.validate();
        this.scopeAwareProvider = builder.scopeAwareProvider;
        this.scheduler = builder.scheduler;
        this.serializer = builder.serializer;
        this.transactionManager = builder.transactionManager;
        this.spanFactory = builder.spanFactory;
        this.useBinaryPojo = builder.useBinaryPojo;
        this.startScheduler = builder.startScheduler;
        this.stopScheduler = builder.stopScheduler;
        this.messageNameResolver = builder.messageNameResolver;
    }

    @Override
    public String schedule(@Nonnull Instant triggerDateTime, @Nonnull String deadlineName,
                           @Nullable Object messageOrPayload,
                           @Nonnull ScopeDescriptor deadlineScope) {
        DeadlineMessage<Object> deadlineMessage = asDeadlineMessage(deadlineName, messageOrPayload, triggerDateTime);
        String identifier = IdentifierFactory.getInstance().generateIdentifier();
        DbSchedulerDeadlineToken taskInstanceId = new DbSchedulerDeadlineToken(identifier);
        Span span = spanFactory.createScheduleSpan(deadlineName, identifier, deadlineMessage);
        runOnPrepareCommitOrNow(span.wrapRunnable(() -> {
            DeadlineMessage<Object> message = processDispatchInterceptors(deadlineMessage);
            TaskInstance<?> taskInstance;
            if (useBinaryPojo) {
                taskInstance = binaryTask(deadlineName, deadlineScope, message, taskInstanceId);
            } else {
                taskInstance = humanReadableTask(deadlineName, deadlineScope, message, taskInstanceId);
            }
            scheduler.schedule(taskInstance, triggerDateTime);
            logger.debug("Task with id: [{}] was successfully created.", identifier);
        }));
        return identifier;
    }

    private TaskInstance<?> binaryTask(
            String deadlineName,
            ScopeDescriptor deadlineScope,
            DeadlineMessage<Object> interceptedDeadlineMessage,
            DbSchedulerDeadlineToken taskInstanceId
    ) {
        DbSchedulerBinaryDeadlineDetails details = DbSchedulerBinaryDeadlineDetails.serialized(
                deadlineName,
                deadlineScope,
                interceptedDeadlineMessage,
                serializer);
        return binaryTaskDescriptor.instance(taskInstanceId.getId(), details);
    }

    private TaskInstance<?> humanReadableTask(
            String deadlineName,
            ScopeDescriptor deadlineScope,
            DeadlineMessage<Object> interceptedDeadlineMessage,
            DbSchedulerDeadlineToken taskInstanceId
    ) {
        DbSchedulerHumanReadableDeadlineDetails details = DbSchedulerHumanReadableDeadlineDetails.serialized(
                deadlineName,
                deadlineScope,
                interceptedDeadlineMessage,
                serializer);
        return humanReadableTaskDescriptor.instance(taskInstanceId.getId(), details);
    }

    /**
     * Gives the {@link Task} using {@link DbSchedulerBinaryDeadlineDetails} to execute a deadline via a
     * {@link Scheduler}. To be able to execute the task, this should be added to the task list, used to create the
     * scheduler.
     *
     * @param deadlineManagerSupplier a {@link Supplier} of a {@link DbSchedulerDeadlineManager}. Preferably a method
     *                                involving dependency injection is used. When those are not available the
     *                                {@link DbSchedulerDeadlineManagerSupplier} can be used instead.
     * @return a {@link Task} to execute a deadline
     */
    public static Task<DbSchedulerBinaryDeadlineDetails> binaryTask(
            Supplier<DbSchedulerDeadlineManager> deadlineManagerSupplier) {
        return new Tasks.OneTimeTaskBuilder<>(TASK_NAME, DbSchedulerBinaryDeadlineDetails.class)
                .execute((taskInstance, context) -> {
                    DbSchedulerDeadlineManager deadlineManager = deadlineManagerSupplier.get();
                    if (isNull(deadlineManager)) {
                        throw new DeadlineManagerNotSuppliedException();
                    }
                    deadlineManager.execute(taskInstance.getId(), taskInstance.getData());
                });
    }

    /**
     * Gives the {@link Task} using {@link DbSchedulerHumanReadableDeadlineDetails} to execute a deadline via a
     * {@link Scheduler}. To be able to execute the task, this should be added to the task list, used to create the
     * scheduler.
     *
     * @param deadlineManagerSupplier a {@link Supplier} of a {@link DbSchedulerDeadlineManager}. Preferably a method
     *                                involving dependency injection is used. When those are not available the
     *                                {@link DbSchedulerDeadlineManagerSupplier} can be used instead.
     * @return a {@link Task} to execute a deadline
     */
    public static Task<DbSchedulerHumanReadableDeadlineDetails> humanReadableTask(
            Supplier<DbSchedulerDeadlineManager> deadlineManagerSupplier) {
        return new Tasks.OneTimeTaskBuilder<>(TASK_NAME, DbSchedulerHumanReadableDeadlineDetails.class)
                .execute((taskInstance, context) -> {
                    DbSchedulerDeadlineManager deadlineManager = deadlineManagerSupplier.get();
                    if (isNull(deadlineManager)) {
                        throw new DeadlineManagerNotSuppliedException();
                    }
                    deadlineManager.execute(taskInstance.getId(), taskInstance.getData());
                });
    }

    @Override
    public void cancelSchedule(@Nonnull String deadlineName, @Nonnull String scheduleId) {
        Span span = spanFactory.createCancelScheduleSpan(deadlineName, scheduleId);
        runOnPrepareCommitOrNow(span.wrapRunnable(
                () -> scheduler.cancel(new DbSchedulerDeadlineToken(scheduleId)))
        );
    }

    @Override
    public void cancelAll(@Nonnull String deadlineName) {
        Span span = spanFactory.createCancelAllSpan(deadlineName);
        if (useBinaryPojo) {
            runOnPrepareCommitOrNow(span.wrapRunnable(
                    () -> scheduler.fetchScheduledExecutionsForTask(
                            TASK_NAME,
                            DbSchedulerBinaryDeadlineDetails.class,
                            cancelIfBinaryDeadlineMatches(deadlineName)
                    )));
        } else {
            runOnPrepareCommitOrNow(span.wrapRunnable(
                    () -> scheduler.fetchScheduledExecutionsForTask(
                            TASK_NAME,
                            DbSchedulerHumanReadableDeadlineDetails.class,
                            cancelIfHumanReadableDeadlineMatches(deadlineName)
                    )));
        }
    }

    private Consumer<ScheduledExecution<DbSchedulerBinaryDeadlineDetails>> cancelIfBinaryDeadlineMatches(
            @Nonnull String deadlineName
    ) {
        return scheduledExecution -> {
            if (deadlineName.equals(scheduledExecution.getData().getD())) {
                scheduler.cancel(scheduledExecution.getTaskInstance());
            }
        };
    }

    private Consumer<ScheduledExecution<DbSchedulerHumanReadableDeadlineDetails>> cancelIfHumanReadableDeadlineMatches(
            @Nonnull String deadlineName
    ) {
        return scheduledExecution -> {
            if (deadlineName.equals(scheduledExecution.getData().getDeadlineName())) {
                scheduler.cancel(scheduledExecution.getTaskInstance());
            }
        };
    }

    @Override
    public void cancelAllWithinScope(@Nonnull String deadlineName, @Nonnull ScopeDescriptor scope) {
        Span span = spanFactory.createCancelAllWithinScopeSpan(deadlineName, scope);
        if (useBinaryPojo) {
            runOnPrepareCommitOrNow(span.wrapRunnable(
                    () -> {
                        SerializedObject<byte[]> serializedDescriptor = serializer.serialize(scope, byte[].class);
                        scheduler.fetchScheduledExecutionsForTask(
                                TASK_NAME,
                                DbSchedulerBinaryDeadlineDetails.class,
                                cancelIfDeadlineAndScopeMatches(deadlineName, serializedDescriptor.getData()));
                    }));
        } else {
            runOnPrepareCommitOrNow(span.wrapRunnable(
                    () -> {
                        SerializedObject<String> serializedDescriptor = serializer.serialize(scope, String.class);
                        scheduler.fetchScheduledExecutionsForTask(
                                TASK_NAME,
                                DbSchedulerHumanReadableDeadlineDetails.class,
                                cancelIfDeadlineAndScopeMatches(deadlineName, serializedDescriptor.getData()));
                    }));
        }
    }

    private Consumer<ScheduledExecution<DbSchedulerHumanReadableDeadlineDetails>> cancelIfDeadlineAndScopeMatches(
            @Nonnull String deadlineName,
            @Nonnull String scopeDescriptor
    ) {
        return scheduledExecution -> {
            DbSchedulerHumanReadableDeadlineDetails data = scheduledExecution.getData();
            if (deadlineName.equals(data.getDeadlineName()) && scopeDescriptor.equals(data.getScopeDescriptor())) {
                scheduler.cancel(scheduledExecution.getTaskInstance());
            }
        };
    }

    private Consumer<ScheduledExecution<DbSchedulerBinaryDeadlineDetails>> cancelIfDeadlineAndScopeMatches(
            @Nonnull String deadlineName,
            @Nonnull byte[] scopeDescriptor
    ) {
        return scheduledExecution -> {
            DbSchedulerBinaryDeadlineDetails data = scheduledExecution.getData();
            if (deadlineName.equals(data.getD()) && Arrays.equals(scopeDescriptor, data.getS())) {
                scheduler.cancel(scheduledExecution.getTaskInstance());
            }
        };
    }

    /**
     * This function is used by the {@link #binaryTask(Supplier)} to execute the deadline.
     *
     * @param deadlineDetails {@link DbSchedulerBinaryDeadlineDetails} containing the needed details to execute.
     */
    @SuppressWarnings("rawtypes")
    private void execute(String deadlineId, DbSchedulerBinaryDeadlineDetails deadlineDetails) {
        GenericDeadlineMessage deadlineMessage = deadlineDetails.asDeadLineMessage(serializer);
        ScopeDescriptor scopeDescriptor = deadlineDetails.getDeserializedScopeDescriptor(serializer);
        execute(deadlineId, deadlineDetails.getD(), deadlineMessage, scopeDescriptor);
    }

    /**
     * This function is used by the {@link #binaryTask(Supplier)} to execute the deadline.
     *
     * @param deadlineDetails {@link DbSchedulerHumanReadableDeadlineDetails} containing the needed details to execute.
     */
    @SuppressWarnings("rawtypes")
    private void execute(String deadlineId, DbSchedulerHumanReadableDeadlineDetails deadlineDetails) {
        GenericDeadlineMessage deadlineMessage = deadlineDetails.asDeadLineMessage(serializer);
        ScopeDescriptor scopeDescriptor = deadlineDetails.getDeserializedScopeDescriptor(serializer);
        execute(deadlineId, deadlineDetails.getDeadlineName(), deadlineMessage, scopeDescriptor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void execute(String deadlineId, String deadlineName, GenericDeadlineMessage deadlineMessage,
                         ScopeDescriptor scopeDescriptor) {
        Span span = spanFactory.createExecuteSpan(deadlineName, deadlineId, deadlineMessage)
                               .start();
        try (SpanScope ignored = span.makeCurrent()) {
            UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(deadlineMessage);
            unitOfWork.attachTransaction(transactionManager);
            unitOfWork.onRollback(uow -> span.recordException(uow.getExecutionResult().getExceptionResult()));
            InterceptorChain chain = new DefaultInterceptorChain<>(
                    unitOfWork,
                    handlerInterceptors(),
                    interceptedDeadlineMessage -> {
                        executeScheduledDeadline(interceptedDeadlineMessage, scopeDescriptor);
                        return null;
                    });
            ResultMessage<?> resultMessage = unitOfWork.executeWithResult(chain::proceedSync);
            if (resultMessage.isExceptional()) {
                Throwable e = resultMessage.exceptionResult();
                span.recordException(e);
                logger.warn("An error occurred while triggering deadline with name [{}].", deadlineName);
                throw new DeadlineException("Failed to process", e);
            }
        } finally {
            span.end();
        }
    }

    private void executeScheduledDeadline(DeadlineMessage<?> deadlineMessage, ScopeDescriptor deadlineScope) {
        scopeAwareProvider.provideScopeAwareStream(deadlineScope)
                          .filter(scopeAwareComponent -> scopeAwareComponent.canResolve(deadlineScope))
                          .forEach(scopeAwareComponent -> {
                              try {
                                  scopeAwareComponent.send(deadlineMessage, deadlineScope);
                              } catch (Exception e) {
                                  String exceptionMessage = format(
                                          "Failed to send a DeadlineMessage for scope [%s]",
                                          deadlineScope.scopeDescription()
                                  );
                                  throw new ExecutionException(exceptionMessage, e);
                              }
                          });
    }

    /**
     * Will start the {@link Scheduler} depending on its current state and the value of {@code startScheduler},
     */
    public void start() {
        if (!startScheduler) {
            return;
        }
        SchedulerState state = scheduler.getSchedulerState();
        if (state.isShuttingDown()) {
            logger.warn("Scheduler is shutting down - will not attempting to start");
            return;
        }
        if (state.isStarted()) {
            logger.info("Scheduler already started - will not attempt to start again");
            return;
        }
        logger.info("Triggering scheduler start");
        scheduler.start();
    }

    @Override
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true) && stopScheduler) {
            scheduler.stop();
        }
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.INBOUND_EVENT_CONNECTORS, this::start);
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS, this::shutdown);
    }

    /**
     * Builder class to instantiate a {@link DbSchedulerDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}, the {@link DefaultDeadlineManagerSpanFactory} defaults
     * to a {@link DefaultDeadlineManagerSpanFactory} backed by a {@link NoOpSpanFactory}. The {@code useBinaryPojo} and {@code startScheduler} are defaulted to
     * {@code true}.
     * <p>
     * The {@link JobScheduler}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard requirements</b> and as
     * such should be provided.
     */
    public static class Builder {

        private Scheduler scheduler;
        private ScopeAwareProvider scopeAwareProvider;
        private Serializer serializer;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private DeadlineManagerSpanFactory spanFactory = DefaultDeadlineManagerSpanFactory.builder()
                                                                                          .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                                          .build();
        private MessageNameResolver messageNameResolver = new ClassBasedMessageNameResolver();

        private boolean useBinaryPojo = true;
        private boolean startScheduler = true;
        private boolean stopScheduler = true;

        /**
         * Sets the {@link Scheduler} used for scheduling and triggering purposes of deadlines. It should have either
         * the {@link #binaryTask(Supplier)} or the {@link #humanReadableTask(Supplier)} from this class as one of its
         * tasks to work. Which one depends on the setting of {@code useBinaryPojo}. When {@code true}, use
         * {@link #binaryTask(Supplier)} else {@link #humanReadableTask(Supplier)}. Depending on you application, you
         * can manage when to start the scheduler, or leave {@code startScheduler} to true, to start it via the
         * {@link Lifecycle}.
         *
         * @param scheduler a {@link Scheduler} used for scheduling and triggering purposes of the deadlines
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scheduler(Scheduler scheduler) {
            assertNonNull(scheduler, "scheduler may not be null");
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Sets the {@link ScopeAwareProvider} which is capable of providing a stream of
         * {@link org.axonframework.messaging.Scope} instances for a given {@link ScopeDescriptor}. Used to return the
         * right Scope to trigger a deadline in.
         *
         * @param scopeAwareProvider a {@link ScopeAwareProvider} used to find the right
         *                           {@link org.axonframework.messaging.Scope} to trigger a deadline in
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scopeAwareProvider(ScopeAwareProvider scopeAwareProvider) {
            assertNonNull(scopeAwareProvider, "ScopeAwareProvider may not be null");
            this.scopeAwareProvider = scopeAwareProvider;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@code payload},
         * {@link org.axonframework.messaging.MetaData} and the {@link ScopeDescriptor} into the {@link DeadlineDetails}
         * as well as the whole {@link DeadlineDetails} itself.
         *
         * @param serializer a {@link Serializer} used to de-/serialize the {@code payload},
         *                   {@link org.axonframework.messaging.MetaData} and the {@link ScopeDescriptor} into the
         *                   {@link DeadlineDetails}, as well as the whole {@link DeadlineDetails} itself.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to build transactions and ties them to deadline. Defaults to a
         * {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to build transactions and ties them to deadline
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link DeadlineManagerSpanFactory} implementation to use for providing tracing capabilities.
         * Defaults to a {@link DefaultDeadlineManagerSpanFactory} backed by a {@link NoOpSpanFactory} by default, which
         * provides no tracing capabilities.
         *
         * @param spanFactory The {@link DeadlineManagerSpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull DeadlineManagerSpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Sets whether to use a pojo optimized for size, {@link DbSchedulerBinaryEventData}, compared to a pojo
         * optimized for readability, {@link DbSchedulerEventScheduler}.
         *
         * @param useBinaryPojo a {@code boolean} to determine whether to use a binary format.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder useBinaryPojo(boolean useBinaryPojo) {
            this.useBinaryPojo = useBinaryPojo;
            return this;
        }

        /**
         * Sets whether to start the {@link Scheduler} using the {@link Lifecycle}, or to never start the scheduler from
         * this component instead. defaults to {@code true}.
         *
         * @param startScheduler a {@code boolean} to determine whether to start the scheduler.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder startScheduler(boolean startScheduler) {
            this.startScheduler = startScheduler;
            return this;
        }

        /**
         * Sets whether to stop the {@link Scheduler} using the {@link Lifecycle}, or to never stop the scheduler from
         * this component instead. defaults to {@code true}.
         *
         * @param stopScheduler a {@code boolean} to determine whether to start the scheduler.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder stopScheduler(boolean stopScheduler) {
            this.stopScheduler = stopScheduler;
            return this;
        }

        /**
         * Sets the {@link MessageNameResolver} used to resolve the {@link QualifiedName} when publishing {@link EventMessage EventMessages}.
         * If not set, a {@link ClassBasedMessageNameResolver} is used by default.
         *
         * @param messageNameResolver The {@link MessageNameResolver} used to provide the {@link QualifiedName} for {@link EventMessage EventMessages}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder messageNameResolver(MessageNameResolver messageNameResolver) {
            assertNonNull(messageNameResolver, "MessageNameResolver may not be null");
            this.messageNameResolver = messageNameResolver;
            return this;
        }

        /**
         * Initializes a {@link DbSchedulerDeadlineManager} as specified through this Builder.
         *
         * @return a {@link DbSchedulerDeadlineManager} as specified through this Builder
         */
        public DbSchedulerDeadlineManager build() {
            return new DbSchedulerDeadlineManager(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(scopeAwareProvider, "The ScopeAwareProvider is a hard requirement and should be provided.");
            assertNonNull(scheduler, "The Scheduler is a hard requirement and should be provided.");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided.");
        }
    }
}

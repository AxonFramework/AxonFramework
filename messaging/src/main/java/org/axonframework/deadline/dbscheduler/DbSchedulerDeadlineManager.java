/*
 * Copyright (c) 2010-2023. Axon Framework
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
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.TaskWithDataDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.AbstractDeadlineManager;
import org.axonframework.deadline.DeadlineException;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.jobrunr.DeadlineDetails;
import org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerBinaryEventData;
import org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerEventScheduler;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.SpanScope;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.deadline.GenericDeadlineMessage.asDeadlineMessage;
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
public class DbSchedulerDeadlineManager extends AbstractDeadlineManager {

    private static final Logger logger = getLogger(DbSchedulerDeadlineManager.class);
    private static final AtomicReference<DbSchedulerDeadlineManager> deadlineManagerReference = new AtomicReference<>();
    private static final TaskWithDataDescriptor<DbSchedulerBinaryDeadlineDetails> binaryTaskDescriptor =
            new TaskWithDataDescriptor<>(TASK_NAME, DbSchedulerBinaryDeadlineDetails.class);
    private static final TaskWithDataDescriptor<DbSchedulerHumanReadableDeadlineDetails> humanReadableTaskDescriptor =
            new TaskWithDataDescriptor<>(TASK_NAME, DbSchedulerHumanReadableDeadlineDetails.class);

    private final ScopeAwareProvider scopeAwareProvider;
    private final Scheduler scheduler;
    private final Serializer serializer;
    private final TransactionManager transactionManager;
    private final SpanFactory spanFactory;
    private final boolean useBinaryPojo;

    /**
     * Instantiate a Builder to be able to create a {@link DbSchedulerDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link SpanFactory} is defaulted to a {@link NoOpSpanFactory}.
     * <p>
     * The {code useBinaryPojo} is defaulted to {@code true}.
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
        deadlineManagerReference.set(this);
    }

    @Override
    public String schedule(@Nonnull Instant triggerDateTime, @Nonnull String deadlineName,
                           @Nullable Object messageOrPayload,
                           @Nonnull ScopeDescriptor deadlineScope) {
        DeadlineMessage<Object> deadlineMessage = asDeadlineMessage(deadlineName, messageOrPayload, triggerDateTime);
        DbSchedulerDeadlineToken taskInstanceId = new DbSchedulerDeadlineToken(UUID.randomUUID().toString());
        Span span = spanFactory.createDispatchSpan(() -> "DbSchedulerDeadlineManager.schedule(" + deadlineName + ")",
                                                   deadlineMessage);
        runOnPrepareCommitOrNow(span.wrapRunnable(() -> {
            DeadlineMessage<Object> message = processDispatchInterceptors(deadlineMessage);
            TaskInstance<?> taskInstance;
            if (useBinaryPojo) {
                taskInstance = binaryTask(deadlineName, deadlineScope, message, taskInstanceId);
            } else {
                taskInstance = humanReadableTask(deadlineName, deadlineScope, message, taskInstanceId);
            }
            scheduler.schedule(taskInstance, triggerDateTime);
            logger.debug("Task with id: [{}] was successfully created.", taskInstanceId.getId());
        }));
        return taskInstanceId.getId();
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
     * @return a {@link Task} to execute a deadline
     */
    public static Task<DbSchedulerBinaryDeadlineDetails> binaryTask() {
        return new Tasks.OneTimeTaskBuilder<>(TASK_NAME, DbSchedulerBinaryDeadlineDetails.class)
                .execute((taskInstance, context) -> {
                    DbSchedulerDeadlineManager deadlineManager = deadlineManagerReference.get();
                    if (isNull(deadlineManager)) {
                        throw new DeadlineManagerNotSetException();
                    }
                    deadlineManager.execute(taskInstance.getData());
                });
    }

    /**
     * Gives the {@link Task} using {@link DbSchedulerHumanReadableDeadlineDetails} to execute a deadline via a
     * {@link Scheduler}. To be able to execute the task, this should be added to the task list, used to create the
     * scheduler.
     *
     * @return a {@link Task} to execute a deadline
     */
    public static Task<DbSchedulerHumanReadableDeadlineDetails> humanReadableTask() {
        return new Tasks.OneTimeTaskBuilder<>(TASK_NAME, DbSchedulerHumanReadableDeadlineDetails.class)
                .execute((taskInstance, context) -> {
                    DbSchedulerDeadlineManager deadlineManager = deadlineManagerReference.get();
                    if (isNull(deadlineManager)) {
                        throw new DeadlineManagerNotSetException();
                    }
                    deadlineManager.execute(taskInstance.getData());
                });
    }

    @Override
    public void cancelSchedule(@Nonnull String deadlineName, @Nonnull String scheduleId) {
        Span span = spanFactory.createInternalSpan(
                () -> "DbSchedulerDeadlineManager.cancelSchedule(" + deadlineName + "," + scheduleId + ")");
        runOnPrepareCommitOrNow(span.wrapRunnable(
                () -> scheduler.cancel(new DbSchedulerDeadlineToken(scheduleId)))
        );
    }

    @Override
    public void cancelAll(@Nonnull String deadlineName) {
        Span span = spanFactory.createInternalSpan(() -> "DbSchedulerDeadlineManager.cancelAll(" + deadlineName + ")");
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
        Span span = spanFactory.createInternalSpan(
                () -> "DbSchedulerDeadlineManager.cancelAllWithinScope(" + deadlineName + ")"
        );
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
     * This function is used by the {@link #binaryTask()} to execute the deadline.
     *
     * @param deadlineDetails {@link DbSchedulerBinaryDeadlineDetails} containing the needed details to execute.
     */
    @SuppressWarnings("rawtypes")
    private void execute(DbSchedulerBinaryDeadlineDetails deadlineDetails) {
        GenericDeadlineMessage deadlineMessage = deadlineDetails.asDeadLineMessage(serializer);
        ScopeDescriptor scopeDescriptor = deadlineDetails.getDeserializedScopeDescriptor(serializer);
        execute(deadlineDetails.getD(), deadlineMessage, scopeDescriptor);
    }

    /**
     * This function is used by the {@link #binaryTask()} to execute the deadline.
     *
     * @param deadlineDetails {@link DbSchedulerHumanReadableDeadlineDetails} containing the needed details to execute.
     */
    @SuppressWarnings("rawtypes")
    private void execute(DbSchedulerHumanReadableDeadlineDetails deadlineDetails) {
        GenericDeadlineMessage deadlineMessage = deadlineDetails.asDeadLineMessage(serializer);
        ScopeDescriptor scopeDescriptor = deadlineDetails.getDeserializedScopeDescriptor(serializer);
        execute(deadlineDetails.getDeadlineName(), deadlineMessage, scopeDescriptor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void execute(String deadlineName, GenericDeadlineMessage deadlineMessage, ScopeDescriptor scopeDescriptor) {
        Span span = spanFactory.createLinkedHandlerSpan(() -> "DeadlineJob.execute", deadlineMessage).start();
        try (SpanScope ignored = span.makeCurrent()) {
            UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(deadlineMessage);
            unitOfWork.attachTransaction(transactionManager);
            unitOfWork.onRollback(uow -> span.recordException(uow.getExecutionResult().getExceptionResult()));
            unitOfWork.onCleanup(uow -> span.end());
            InterceptorChain chain = new DefaultInterceptorChain<>(
                    unitOfWork,
                    handlerInterceptors(),
                    interceptedDeadlineMessage -> {
                        executeScheduledDeadline(interceptedDeadlineMessage, scopeDescriptor);
                        return null;
                    });
            ResultMessage<?> resultMessage = unitOfWork.executeWithResult(chain::proceed);
            if (resultMessage.isExceptional()) {
                Throwable e = resultMessage.exceptionResult();
                span.recordException(e);
                logger.warn("An error occurred while triggering deadline with name [{}].", deadlineName);
                throw new DeadlineException("Failed to process", e);
            }
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

    @Override
    public void shutdown() {
        scheduler.stop();
        deadlineManagerReference.set(null);
    }

    /**
     * Builder class to instantiate a {@link DbSchedulerDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}, the {@link SpanFactory} defaults
     * to a {@link NoOpSpanFactory} and the {code useBinaryPojo} is defaulted to {@code true}.
     * <p>
     * The {@link JobScheduler}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard requirements</b> and as
     * such should be provided.
     */
    public static class Builder {

        private Scheduler scheduler;
        private ScopeAwareProvider scopeAwareProvider;
        private Serializer serializer;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private SpanFactory spanFactory = NoOpSpanFactory.INSTANCE;
        private boolean useBinaryPojo = true;

        /**
         * Sets the {@link Scheduler} used for scheduling and triggering purposes of the deadlines. It should have this
         * components {@link #binaryTask()} as one of its tasks to work.
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
         * Sets the {@link SpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link NoOpSpanFactory} by default, which provides no tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull SpanFactory spanFactory) {
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

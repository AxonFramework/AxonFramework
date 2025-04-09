/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.deadline.jobrunr;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.deadline.AbstractDeadlineManager;
import org.axonframework.deadline.DeadlineException;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineManagerSpanFactory;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.DefaultDeadlineManagerSpanFactory;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.quartz.QuartzDeadlineManager;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.SpanScope;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.states.IllegalJobStateChangeException;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.deadline.jobrunr.LabelUtils.getCombinedLabel;
import static org.axonframework.deadline.jobrunr.LabelUtils.getLabel;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of {@link DeadlineManager} that delegates scheduling and triggering to a Jobrunr
 * {@link JobScheduler}.
 *
 * @author Tom de Backer
 * @author Gerard Klijs
 * @since 4.7.0
 */
public class JobRunrDeadlineManager extends AbstractDeadlineManager implements Lifecycle {

    private static final Logger logger = getLogger(JobRunrDeadlineManager.class);
    protected static final String DELETE_REASON = "Deleted via Axon DeadlineManager API";
    private static final String NOT_SUPPORTED_MSG =
            "The '%s' method is not supported without using JobRunrPro with the JobRunrProDeadlineManager.\n"
                    + "Move to the pro version and the extension or use 'cancelSchedule' method instead.\n"
                    + "Using 'cancelSchedule' requires keeping track of the returned 'scheduleId' from invoking 'schedule'.";

    private final ScopeAwareProvider scopeAwareProvider;
    private final JobScheduler jobScheduler;
    private final Serializer serializer;
    private final TransactionManager transactionManager;
    private final DeadlineManagerSpanFactory spanFactory;

    /**
     * Instantiate a Builder to be able to create a {@link JobRunrDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link SpanFactory} is defaulted to a {@link NoOpSpanFactory}.
     * <p>
     * The {@link JobScheduler}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard requirements</b> and as
     * such should be provided.
     *
     * @return a Builder to be able to create a {@link JobRunrDeadlineManager}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link JobRunrDeadlineManager} based on the fields contained in the
     * {@link JobRunrDeadlineManager.Builder}.
     * <p>
     * Will assert that the {@link ScopeAwareProvider}, {@link JobScheduler} and {@link Serializer} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link QuartzDeadlineManager.Builder} used to instantiate a {@link QuartzDeadlineManager}
     *                instance
     */
    protected JobRunrDeadlineManager(Builder builder) {
        builder.validate();
        this.scopeAwareProvider = builder.scopeAwareProvider;
        this.jobScheduler = builder.jobScheduler;
        this.serializer = builder.serializer;
        this.transactionManager = builder.transactionManager;
        this.spanFactory = builder.spanFactory;
        this.messageTypeResolver = builder.messageTypeResolver;
    }

    /**
     * Provides the class to use in spans,
     *
     * @return the class name
     */
    @SuppressWarnings("squid:S3400")
    protected String getSpanClassName() {
        return "JobRunrDeadlineManager";
    }

    @Override
    public String schedule(@Nonnull Instant triggerDateTime, @Nonnull String deadlineName,
                           @Nullable Object messageOrPayload,
                           @Nonnull ScopeDescriptor deadlineScope) {
        DeadlineMessage<Object> deadlineMessage = asDeadlineMessage(deadlineName, messageOrPayload, triggerDateTime);
        UUID deadlineId = UUID.randomUUID();
        Span span = spanFactory.createScheduleSpan(deadlineName, deadlineId.toString(), deadlineMessage);
        runOnPrepareCommitOrNow(span.wrapRunnable(() -> {
            DeadlineMessage<Object> interceptedDeadlineMessage = processDispatchInterceptors(deadlineMessage);
            String serializedDeadlineDetails = DeadlineDetails.serialized(
                    deadlineName,
                    deadlineScope,
                    interceptedDeadlineMessage,
                    serializer);
            String combinedLabel = getCombinedLabel(serializer, deadlineName, deadlineScope);
            JobBuilder job = JobBuilder.aJob()
                                       .withId(deadlineId)
                                       .withName(deadlineName)
                                       .withLabels(getLabel(deadlineName), combinedLabel)
                                       .withDetails(() -> this.execute(serializedDeadlineDetails, deadlineId.toString()))
                                       .scheduleAt(triggerDateTime);
            JobId id = jobScheduler.create(job);
            logger.debug("Job with id: [{}] was successfully created.", id);
        }));
        return deadlineId.toString();
    }

    @Override
    public void cancelSchedule(@Nonnull String deadlineName, @Nonnull String scheduleId) {
        Span span = spanFactory.createCancelScheduleSpan(deadlineName, scheduleId);
        runOnPrepareCommitOrNow(span.wrapRunnable(() -> {
            try {
                jobScheduler.delete(toUuid(scheduleId), DELETE_REASON);
            } catch (IllegalJobStateChangeException e) {
                if (!tryingToDeleteAlreadyDeletedJob(e.getFrom(), e.getTo())) {
                    throw e;
                }
            }
        }));
    }

    /**
     * Protective measure to ensure that if the scheduled job was already deleted, we ignore a second try to delete the
     * job.
     */
    private static boolean tryingToDeleteAlreadyDeletedJob(StateName from, StateName to) {
        return from == StateName.DELETED && to == StateName.DELETED;
    }

    @Override
    public void cancelAll(@Nonnull String deadlineName) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_MSG, "cancelAll"));
    }

    private UUID toUuid(@Nonnull String scheduleId) {
        try {
            return UUID.fromString(scheduleId);
        } catch (IllegalArgumentException e) {
            throw new DeadlineException("For JobRunr the scheduleId should be an UUID representation.", e);
        }
    }

    @Override
    public void cancelAllWithinScope(@Nonnull String deadlineName, @Nonnull ScopeDescriptor scope) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_MSG, "cancelAllWithinScope"));
    }

    /**
     * This function should only be called via JobRunr when a deadline was triggered. It will try to execute the
     * scheduled deadline on the set scope. It will throw a {@link DeadlineException} in case of errors such that they
     * will be optionally retried by JobRunr.
     *
     * @param serializedDeadlineDetails {@code byte[]} containing the serialized {@link DeadlineDetails} object with all
     *                                  the needed details to execute.
     * @param deadlineId                The {@link UUID} of the deadline.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void execute(@Nonnull String serializedDeadlineDetails, String deadlineId) {
        SimpleSerializedObject<String> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                serializedDeadlineDetails, String.class, DeadlineDetails.class.getName(), null
        );
        DeadlineDetails deadlineDetails = serializer.deserialize(serializedDeadlineMetaData);
        GenericDeadlineMessage deadlineMessage = deadlineDetails.asDeadLineMessage(serializer);
        Span span = spanFactory.createExecuteSpan(deadlineDetails.getDeadlineName(),
                                                  deadlineId,
                                                  deadlineMessage).start();
        try (SpanScope ignored = span.makeCurrent()) {
            UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(deadlineMessage);
            unitOfWork.attachTransaction(transactionManager);
            unitOfWork.onRollback(uow -> span.recordException(uow.getExecutionResult().getExceptionResult()));
            InterceptorChain chain = new DefaultInterceptorChain<>(
                    unitOfWork,
                    handlerInterceptors(),
                    interceptedDeadlineMessage -> {
                        executeScheduledDeadline(interceptedDeadlineMessage,
                                                 deadlineDetails.getDeserializedScopeDescriptor(serializer));
                        return null;
                    });
            ResultMessage<?> resultMessage = unitOfWork.executeWithResult(chain::proceedSync);
            if (resultMessage.isExceptional()) {
                Throwable e = resultMessage.exceptionResult();
                span.recordException(e);
                logger.warn("An error occurred while triggering deadline with name [{}].",
                            deadlineDetails.getDeadlineName());
                throw new DeadlineException("Failed to process", e);
            }
        } finally {
            span.end();
        }
    }

    @SuppressWarnings("Duplicates")
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
        jobScheduler.shutdown();
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS, this::shutdown);
    }

    /**
     * Builder class to instantiate a {@link JobRunrDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager} and the {@link DeadlineManagerSpanFactory}
     * defaults to {@link DefaultDeadlineManagerSpanFactory} backed by a {@link NoOpSpanFactory}.
     * <p>
     * The {@link JobScheduler}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard requirements</b> and as
     * such should be provided.
     */
    public static class Builder {

        private JobScheduler jobScheduler;
        private ScopeAwareProvider scopeAwareProvider;
        private Serializer serializer;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private DeadlineManagerSpanFactory spanFactory = DefaultDeadlineManagerSpanFactory.builder()
                                                                                          .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                                          .build();
        private MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

        /**
         * Sets the {@link JobScheduler} used for scheduling and triggering purposes of the deadlines.
         *
         * @param jobScheduler a {@link JobScheduler} used for scheduling and triggering purposes of the deadlines
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder jobScheduler(JobScheduler jobScheduler) {
            assertNonNull(jobScheduler, "JobScheduler may not be null");
            this.jobScheduler = jobScheduler;
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
         * Sets the {@link MessageTypeResolver} used to resolve the {@link QualifiedName} when scheduling {@link DeadlineMessage DeadlineMessages}.
         * If not set, a {@link ClassBasedMessageTypeResolver} is used by default.
         *
         * @param messageTypeResolver The {@link MessageTypeResolver} used to provide the {@link QualifiedName} for {@link DeadlineMessage DeadlineMessages}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder messageNameResolver(MessageTypeResolver messageTypeResolver) {
            assertNonNull(messageTypeResolver, "MessageNameResolver may not be null");
            this.messageTypeResolver = messageTypeResolver;
            return this;
        }

        /**
         * Initializes a {@link JobRunrDeadlineManager} as specified through this Builder.
         *
         * @return a {@link JobRunrDeadlineManager} as specified through this Builder
         */
        public JobRunrDeadlineManager build() {
            return new JobRunrDeadlineManager(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(scopeAwareProvider, "The ScopeAwareProvider is a hard requirement and should be provided.");
            assertNonNull(jobScheduler, "The JobScheduler is a hard requirement and should be provided.");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided.");
        }
    }
}

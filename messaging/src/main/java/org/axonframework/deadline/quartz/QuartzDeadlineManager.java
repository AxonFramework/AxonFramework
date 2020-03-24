/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.deadline.quartz;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonNonTransientException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.AbstractDeadlineManager;
import org.axonframework.deadline.DeadlineException;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ExceptionUtils.findException;
import static org.axonframework.deadline.GenericDeadlineMessage.asDeadlineMessage;
import static org.quartz.JobKey.jobKey;

/**
 * Implementation of {@link DeadlineManager} that delegates scheduling and triggering to a Quartz {@link Scheduler}.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3
 */
public class QuartzDeadlineManager extends AbstractDeadlineManager {

    private static final Logger logger = LoggerFactory.getLogger(QuartzDeadlineManager.class);

    private static final String JOB_NAME_PREFIX = "deadline-";

    private final Scheduler scheduler;
    private final ScopeAwareProvider scopeAwareProvider;
    private final TransactionManager transactionManager;
    private final Serializer serializer;
    private final Predicate<Throwable> refireImmediatelyPolicy;

    /**
     * Instantiate a Builder to be able to create a {@link QuartzDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to an {@link NoTransactionManager}, and the {@link Serializer} to a
     * {@link XStreamSerializer}. The {@link Scheduler} and {@link ScopeAwareProvider} are <b>hard requirements</b> and
     * as such should be provided.
     *
     * @return a Builder to be able to create a {@link QuartzDeadlineManager}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link QuartzDeadlineManager} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link Scheduler}, {@link ScopeAwareProvider}, {@link TransactionManager} and
     * {@link Serializer} are not {@code null}, and will throw an {@link AxonConfigurationException} if any of them is
     * {@code null}.
     * The TransactionManager, ScopeAwareProvider and Serializer will be tied to the Scheduler's context. If this
     * initialization step fails, this will too result in an AxonConfigurationException.
     *
     * @param builder the {@link Builder} used to instantiate a {@link QuartzDeadlineManager} instance
     */
    protected QuartzDeadlineManager(Builder builder) {
        builder.validate();
        this.scheduler = builder.scheduler;
        this.scopeAwareProvider = builder.scopeAwareProvider;
        this.transactionManager = builder.transactionManager;
        this.serializer = builder.serializer.get();
        this.refireImmediatelyPolicy = builder.refireImmediatelyPolicy;

        try {
            initialize();
        } catch (SchedulerException e) {
            throw new AxonConfigurationException("Unable to initialize QuartzDeadlineManager", e);
        }
    }

    private void initialize() throws SchedulerException {
        scheduler.getContext().put(DeadlineJob.TRANSACTION_MANAGER_KEY, transactionManager);
        scheduler.getContext().put(DeadlineJob.SCOPE_AWARE_RESOLVER, scopeAwareProvider);
        scheduler.getContext().put(DeadlineJob.JOB_DATA_SERIALIZER, serializer);
        scheduler.getContext().put(DeadlineJob.HANDLER_INTERCEPTORS, handlerInterceptors());
        scheduler.getContext().put(DeadlineJob.REFIRE_IMMEDIATELY_POLICY, refireImmediatelyPolicy);
    }

    @Override
    public String schedule(Instant triggerDateTime,
                           String deadlineName,
                           Object messageOrPayload,
                           ScopeDescriptor deadlineScope) {
        DeadlineMessage<Object> deadlineMessage = asDeadlineMessage(deadlineName, messageOrPayload, triggerDateTime);
        String deadlineId = JOB_NAME_PREFIX + deadlineMessage.getIdentifier();

        runOnPrepareCommitOrNow(() -> {
            DeadlineMessage interceptedDeadlineMessage = processDispatchInterceptors(deadlineMessage);
            try {
                JobDetail jobDetail = buildJobDetail(interceptedDeadlineMessage,
                                                     deadlineScope,
                                                     new JobKey(deadlineId, deadlineName));
                scheduler.scheduleJob(jobDetail, buildTrigger(triggerDateTime, jobDetail.getKey()));
            } catch (SchedulerException e) {
                throw new DeadlineException("An error occurred while setting a timer for a deadline", e);
            }
        });

        return deadlineId;
    }

    @Override
    public String schedule(Duration triggerDuration,
                           String deadlineName,
                           Object messageOrPayload,
                           ScopeDescriptor deadlineScope) {
        return schedule(Instant.now().plus(triggerDuration), deadlineName, messageOrPayload, deadlineScope);
    }

    @Override
    public void cancelSchedule(String deadlineName, String scheduleId) {
        runOnPrepareCommitOrNow(() -> cancelSchedule(jobKey(scheduleId, deadlineName)));
    }

    @Override
    public void cancelAll(String deadlineName) {
        runOnPrepareCommitOrNow(() -> {
            try {
                scheduler.getJobKeys(GroupMatcher.groupEquals(deadlineName))
                         .forEach(this::cancelSchedule);
            } catch (SchedulerException e) {
                throw new DeadlineException("An error occurred while cancelling a timer for a deadline manager", e);
            }
        });
    }

    @Override
    public void cancelAllWithinScope(String deadlineName, ScopeDescriptor scope) {
        try {
            Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(deadlineName));
            for (JobKey jobKey : jobKeys) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                ScopeDescriptor jobScope = DeadlineJob.DeadlineJobDataBinder
                        .deadlineScope(serializer, jobDetail.getJobDataMap());
                if (scope.equals(jobScope)) {
                    cancelSchedule(jobKey);
                }
            }
        } catch (SchedulerException e) {
            throw new DeadlineException("An error occurred while cancelling a timer for a deadline manager", e);
        }
    }

    private void cancelSchedule(JobKey jobKey) {
        try {
            if (!scheduler.deleteJob(jobKey)) {
                logger.warn("The job belonging to this token could not be deleted.");
            }
        } catch (SchedulerException e) {
            throw new DeadlineException("An error occurred while cancelling a timer for a deadline manager", e);
        }
    }

    private JobDetail buildJobDetail(DeadlineMessage deadlineMessage, ScopeDescriptor deadlineScope, JobKey jobKey) {
        JobDataMap jobData = DeadlineJob.DeadlineJobDataBinder.toJobData(serializer, deadlineMessage, deadlineScope);
        return JobBuilder.newJob(DeadlineJob.class)
                         .withDescription(deadlineMessage.getPayloadType().getName())
                         .withIdentity(jobKey)
                         .usingJobData(jobData)
                         .build();
    }

    private static Trigger buildTrigger(Instant triggerDateTime, JobKey key) {
        return TriggerBuilder.newTrigger()
                             .forJob(key)
                             .startAt(Date.from(triggerDateTime))
                             .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Will shutdown in the {@link Phase#INBOUND_EVENT_CONNECTORS} phase.
     */
    @Override
    @ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void shutdown() {
        try {
            scheduler.shutdown(true);
        } catch (SchedulerException e) {
            throw new DeadlineException("An error occurred while trying to shutdown the deadline manager", e);
        }
    }

    /**
     * Builder class to instantiate a {@link QuartzDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to an {@link NoTransactionManager}, and the {@link Serializer} to a
     * {@link XStreamSerializer}. The {@link Scheduler} and {@link ScopeAwareProvider} are <b>hard requirements</b> and
     * as such should be provided.
     */
    public static class Builder {

        private Scheduler scheduler;
        private ScopeAwareProvider scopeAwareProvider;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private Supplier<Serializer> serializer = XStreamSerializer::defaultSerializer;
        private Predicate<Throwable> refireImmediatelyPolicy =
                throwable -> !findException(throwable, t -> t instanceof AxonNonTransientException).isPresent();

        /**
         * Sets the {@link Scheduler} used for scheduling and triggering purposes of the deadlines.
         *
         * @param scheduler a {@link Scheduler} used for scheduling and triggering purposes of the deadlines
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scheduler(Scheduler scheduler) {
            assertNonNull(scheduler, "Scheduler may not be null");
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
         * Sets the {@link Serializer} used to de-/serialize the {@link DeadlineMessage} and the {@link ScopeDescriptor}
         * into the {@link JobDataMap}. Defaults to a {@link XStreamSerializer}.
         *
         * @param serializer a {@link Serializer} used to de-/serialize the {@link DeadlineMessage} and the
         *                   {@link ScopeDescriptor} into the {@link JobDataMap}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = () -> serializer;
            return this;
        }

        /**
         * Sets a {@link Predicate} taking a {@link Throwable} to decided whether a failed {@link DeadlineJob} should
         * be 'refired' immediately. Defaults to a Predicate which will refire immediately on
         * non-{@link AxonNonTransientException}s.
         *
         * @param refireImmediatelyPolicy a {@link Predicate} taking a {@link Throwable} to decided whether a failed
         *                                {@link DeadlineJob} should be 'refired' immediately
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder refireImmediatelyPolicy(Predicate<Throwable> refireImmediatelyPolicy) {
            assertNonNull(refireImmediatelyPolicy, "The refire policy may not be null");
            this.refireImmediatelyPolicy = refireImmediatelyPolicy;
            return this;
        }

        /**
         * Initializes a {@link QuartzDeadlineManager} as specified through this Builder.
         *
         * @return a {@link QuartzDeadlineManager} as specified through this Builder
         */
        public QuartzDeadlineManager build() {
            return new QuartzDeadlineManager(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(scheduler, "The Scheduler is a hard requirement and should be provided");
            assertNonNull(scopeAwareProvider, "The ScopeAwareProvider is a hard requirement and should be provided");
        }
    }
}

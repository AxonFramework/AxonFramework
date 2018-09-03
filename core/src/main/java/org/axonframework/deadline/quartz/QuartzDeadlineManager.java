/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.deadline.quartz;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.AbstractDeadlineManager;
import org.axonframework.deadline.DeadlineException;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.messaging.ScopeAware;
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
import java.util.List;

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

    /**
     * Initializes QuartzDeadlineManager with given {@code scheduler} and {@code scopeAwareProvider} which will load
     * and send messages to {@link org.axonframework.messaging.Scope} implementing components. A
     * {@link NoTransactionManager} will be used as the transaction manager and the {@link XStreamSerializer} for
     * serializing the {@link JobDataMap} contents.
     *
     * @param scheduler          A {@link Scheduler} used for scheduling and triggering purposes
     * @param scopeAwareProvider a {@link List} of {@link ScopeAware} components which are able to load and send
     *                           Messages to components which implement {@link org.axonframework.messaging.Scope}
     */
    public QuartzDeadlineManager(Scheduler scheduler, ScopeAwareProvider scopeAwareProvider) {
        this(scheduler, scopeAwareProvider, NoTransactionManager.INSTANCE);
    }

    /**
     * Initializes QuartzDeadlineManager with given {@code scheduler} and {@code scopeAwareProvider} which will load
     * and send messages to {@link org.axonframework.messaging.Scope} implementing components. Uses the {@link
     * XStreamSerializer} for serializing the deadline message and scope in to the {@link JobDataMap}.
     *
     * @param scheduler          A {@link Scheduler} used for scheduling and triggering purposes
     * @param scopeAwareProvider a {@link List} of {@link ScopeAware} components which are able to load and send
     *                           Messages to components which implement {@link org.axonframework.messaging.Scope}
     * @param transactionManager A {@link TransactionManager} which builds transactions and ties them to deadline
     */
    public QuartzDeadlineManager(Scheduler scheduler,
                                 ScopeAwareProvider scopeAwareProvider,
                                 TransactionManager transactionManager) {
        this(scheduler, scopeAwareProvider, transactionManager, new XStreamSerializer());
    }

    /**
     * Initializes QuartzDeadlineManager with given {@code scheduler} and {@code scopeAwareProvider} which will load
     * and send messages to {@link org.axonframework.messaging.Scope} implementing components. Uses the given {@code
     * serializer} for serializing the deadline message and scope in to the {@link JobDataMap}.
     *
     * @param scheduler          A {@link Scheduler} used for scheduling and triggering purposes
     * @param scopeAwareProvider a {@link List} of {@link ScopeAware} components which are able to load and send
     *                           Messages to components which implement {@link org.axonframework.messaging.Scope}
     * @param transactionManager A {@link TransactionManager} which builds transactions and ties them to deadline
     * @param serializer         The {@link Serializer} which will be used to de-/serialize the {@link DeadlineMessage}
     *                           and the {@link ScopeDescriptor} into the {@link JobDataMap}
     */
    public QuartzDeadlineManager(Scheduler scheduler,
                                 ScopeAwareProvider scopeAwareProvider,
                                 TransactionManager transactionManager,
                                 Serializer serializer) {
        this.scheduler = scheduler;
        this.scopeAwareProvider = scopeAwareProvider;
        this.transactionManager = transactionManager;
        this.serializer = serializer;

        try {
            initialize();
        } catch (SchedulerException e) {
            throw new DeadlineException("Unable to initialize quartz scheduler", e);
        }
    }

    private void initialize() throws SchedulerException {
        scheduler.getContext().put(DeadlineJob.TRANSACTION_MANAGER_KEY, transactionManager);
        scheduler.getContext().put(DeadlineJob.SCOPE_AWARE_RESOLVER, scopeAwareProvider);
        scheduler.getContext().put(DeadlineJob.JOB_DATA_SERIALIZER, serializer);
    }

    @Override
    public void schedule(Instant triggerDateTime,
                         String deadlineName,
                         Object messageOrPayload,
                         ScopeDescriptor deadlineScope,
                         String scheduleId) {
        runOnPrepareCommitOrNow(() -> {
            DeadlineMessage deadlineMessage = asDeadlineMessage(deadlineName, messageOrPayload);
            try {
                JobDetail jobDetail = buildJobDetail(deadlineMessage,
                                                     deadlineScope,
                                                     new JobKey(scheduleId, deadlineName));
                scheduler.scheduleJob(jobDetail, buildTrigger(triggerDateTime, jobDetail.getKey()));
            } catch (SchedulerException e) {
                throw new DeadlineException("An error occurred while setting a timer for a deadline", e);
            }
        });
    }

    @Override
    public void schedule(Duration triggerDuration,
                         String deadlineName,
                         Object messageOrPayload,
                         ScopeDescriptor deadlineScope,
                         String scheduleId) {
        schedule(Instant.now().plus(triggerDuration), deadlineName, messageOrPayload, deadlineScope, scheduleId);
    }

    @Override
    public String generateScheduleId() {
        return JOB_NAME_PREFIX + IdentifierFactory.getInstance().generateIdentifier();
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
}

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
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.deadline.DeadlineException;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.DeadlineTargetLoader;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.quartz.QuartzScheduleToken;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;

import static org.axonframework.deadline.GenericDeadlineMessage.asDeadlineMessage;
import static org.quartz.JobKey.jobKey;

/**
 * Implementation of {@link DeadlineManager} that delegates scheduling and triggering to a Quartz Scheduler.
 *
 * @author Milan Savic
 * @since 3.3
 */
// TODO fix this
public class QuartzDeadlineManager /*implements DeadlineManager */{

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzDeadlineManager.class);
    private static final String JOB_NAME_PREFIX = "deadline-";
    private static final String DEFAULT_GROUP_NAME = "AxonFramework-Deadlines";
    private final String groupIdentifier;
    private final Scheduler scheduler;
    private final TransactionManager transactionManager;
    private final DeadlineTargetLoader deadlineTargetLoader;

    /**
     * Initializes Quartz Deadline Manager with {@code scheduler} and {@code deadlineTargetLoader}. As group identifier
     * {@link #DEFAULT_GROUP_NAME} is used, and {@link NoTransactionManager} as transaction manager.
     *
     * @param scheduler            Used for scheduling and triggering purposes
     * @param deadlineTargetLoader Used for loading of target entities in order to handle deadlines
     */
    public QuartzDeadlineManager(Scheduler scheduler, DeadlineTargetLoader deadlineTargetLoader) {
        this(DEFAULT_GROUP_NAME, scheduler, NoTransactionManager.INSTANCE, deadlineTargetLoader);
    }

    /**
     * Initializes Quartz Deadline Manager.
     *
     * @param groupIdentifier      Identifier for quartz job (used in combination with deadline message id)
     * @param scheduler            Used for scheduling and triggering purposes
     * @param transactionManager   Builds transactions and ties them to deadline execution
     * @param deadlineTargetLoader Used for loading of target entities in order to handle deadlines
     */
    public QuartzDeadlineManager(String groupIdentifier, Scheduler scheduler,
                                 TransactionManager transactionManager,
                                 DeadlineTargetLoader deadlineTargetLoader) {
        this.groupIdentifier = groupIdentifier;
        this.scheduler = scheduler;
        this.transactionManager = transactionManager;
        this.deadlineTargetLoader = deadlineTargetLoader;
        try {
            initialize();
        } catch (SchedulerException e) {
            throw new DeadlineException("Unable to initialize quartz scheduler", e);
        }
    }

//    @Override
    public void schedule(Instant triggerDateTime, ScopeDescriptor deadlineScope,
                         Object deadlineInfo, ScheduleToken scheduleToken) {
        QuartzScheduleToken token = convert(scheduleToken);
        DeadlineMessage deadlineMessage = asDeadlineMessage(deadlineInfo);
        try {
            JobDetail jobDetail = buildJobDetail(deadlineMessage,
                                                 deadlineScope,
                                                 new JobKey(token.getJobIdentifier(), token.getGroupIdentifier()));
            scheduler.scheduleJob(jobDetail, buildTrigger(triggerDateTime, jobDetail.getKey()));
        } catch (SchedulerException e) {
            throw new DeadlineException("An error occurred while setting a timer for a deadline", e);
        }
    }

//    @Override
    public void schedule(Duration triggerDuration, ScopeDescriptor deadlineScope,
                         Object deadlineInfo, ScheduleToken scheduleToken) {
        schedule(Instant.now().plus(triggerDuration), deadlineScope, deadlineInfo, scheduleToken);
    }

//    @Override
    public ScheduleToken generateScheduleId() {
        String jobIdentifier = JOB_NAME_PREFIX + IdentifierFactory.getInstance().generateIdentifier();
        return new QuartzScheduleToken(jobIdentifier, groupIdentifier);
    }

//    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        QuartzScheduleToken reference = convert(scheduleToken);
        try {
            if (!scheduler.deleteJob(jobKey(reference.getJobIdentifier(), reference.getGroupIdentifier()))) {
                LOGGER.warn("The job belonging to this token could not be deleted.");
            }
        } catch (SchedulerException e) {
            throw new DeadlineException("An error occurred while cancelling a timer for a deadline manager", e);
        }
    }

    private void initialize() throws SchedulerException {
        scheduler.getContext().put(DeadlineJob.TRANSACTION_MANAGER_KEY, transactionManager);
        scheduler.getContext().put(DeadlineJob.DEADLINE_TARGET_LOADER_KEY, deadlineTargetLoader);
    }

    private JobDetail buildJobDetail(DeadlineMessage deadlineMessage, ScopeDescriptor deadlineScope, JobKey jobKey) {
        JobDataMap jobData = DeadlineJob.DeadlineJobDataBinder.toJobData(deadlineMessage, deadlineScope);
        return JobBuilder.newJob(DeadlineJob.class)
                         .withDescription(deadlineMessage.getPayloadType().getName())
                         .withIdentity(jobKey)
                         .usingJobData(jobData)
                         .build();
    }

    private Trigger buildTrigger(Instant triggerDateTime, JobKey key) {
        return TriggerBuilder.newTrigger()
                             .forJob(key)
                             .startAt(Date.from(triggerDateTime))
                             .build();
    }

    private QuartzScheduleToken convert(ScheduleToken scheduleToken) {
        if (!QuartzScheduleToken.class.isInstance(scheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler");
        }
        return (QuartzScheduleToken) scheduleToken;
    }
}

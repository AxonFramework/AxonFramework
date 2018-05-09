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

import org.axonframework.common.Assert;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineException;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.DeadlineContext;
import org.axonframework.deadline.DeadlineTargetLoader;
import org.axonframework.deadline.GenericDeadlineMessage;
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
import javax.annotation.PostConstruct;

import static org.axonframework.common.Assert.notNull;
import static org.quartz.JobKey.jobKey;

/**
 * Implementation of {@link DeadlineManager} that delegates scheduling and triggering to a Quartz Scheduler.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class QuartzDeadlineManager implements DeadlineManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzDeadlineManager.class);
    private static final String JOB_NAME_PREFIX = "deadline-";
    private static final String DEFAULT_GROUP_NAME = "AxonFramework-Deadlines";
    private String groupIdentifier = DEFAULT_GROUP_NAME;
    private Scheduler scheduler;
    private volatile boolean initialized;
    private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
    private DeadlineTargetLoader deadlineTargetLoader;

    @Override
    public void schedule(Instant triggerDateTime, DeadlineContext deadlineContext,
                         Object deadlineInfo, ScheduleToken scheduleToken) {
        Assert.state(initialized, () -> "Scheduler is not yet initialized");
        QuartzScheduleToken token = convert(scheduleToken);
        DeadlineMessage deadlineMessage = GenericDeadlineMessage.asDeadlineMessage(deadlineInfo);
        try {
            JobDetail jobDetail = buildJobDetail(deadlineMessage,
                                                 deadlineContext,
                                                 new JobKey(token.getJobIdentifier(), token.getGroupIdentifier()));
            scheduler.scheduleJob(jobDetail, buildTrigger(triggerDateTime, jobDetail.getKey()));
        } catch (SchedulerException e) {
            throw new DeadlineException("An error occurred while setting a timer for a deadline", e);
        }
    }

    @Override
    public void schedule(Duration triggerDuration, DeadlineContext deadlineContext,
                         Object deadlineInfo, ScheduleToken scheduleToken) {
        schedule(Instant.now().plus(triggerDuration), deadlineContext, deadlineInfo, scheduleToken);
    }

    @Override
    public ScheduleToken generateToken() {
        String jobIdentifier = JOB_NAME_PREFIX + IdentifierFactory.getInstance().generateIdentifier();
        return new QuartzScheduleToken(jobIdentifier, groupIdentifier);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        Assert.state(initialized, () -> "Scheduler is not yet initialized");

        QuartzScheduleToken reference = convert(scheduleToken);
        try {
            if (!scheduler.deleteJob(jobKey(reference.getJobIdentifier(), reference.getGroupIdentifier()))) {
                LOGGER.warn("The job belonging to this token could not be deleted.");
            }
        } catch (SchedulerException e) {
            throw new DeadlineException("An error occurred while cancelling a timer for a deadline manager", e);
        }
    }

    @PostConstruct
    public void initialize() throws SchedulerException {
        notNull(scheduler, () -> "A Scheduler must be provided.");
        notNull(deadlineTargetLoader, () -> "A DeadlineTargetLoader must be provided.");
        scheduler.getContext().put(DeadlineJob.TRANSACTION_MANAGER_KEY, transactionManager);
        scheduler.getContext().put(DeadlineJob.DEADLINE_TARGET_LOADER_KEY, deadlineTargetLoader);
        initialized = true;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setGroupIdentifier(String groupIdentifier) {
        this.groupIdentifier = groupIdentifier;
    }

    public void setDeadlineTargetLoader(DeadlineTargetLoader deadlineTargetLoader) {
        this.deadlineTargetLoader = deadlineTargetLoader;
    }

    private JobDetail buildJobDetail(DeadlineMessage deadlineMessage, DeadlineContext deadlineContext, JobKey jobKey) {
        JobDataMap jobData = DeadlineJob.DeadlineJobDataBinder.toJobData(deadlineMessage, deadlineContext);
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

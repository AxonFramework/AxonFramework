/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.common.Assert;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.SchedulingException;
import org.axonframework.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.unitofwork.TransactionManager;
import org.axonframework.unitofwork.UnitOfWorkFactory;
import org.joda.time.DateTime;
import org.joda.time.Duration;
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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import static org.quartz.JobKey.jobKey;

/**
 * EventScheduler implementation that delegates scheduling and triggering to a Quartz Scheduler.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class QuartzEventScheduler implements org.axonframework.eventhandling.scheduling.EventScheduler {

    private static final Logger logger = LoggerFactory.getLogger(QuartzEventScheduler.class);
    private static final String JOB_NAME_PREFIX = "event-";
    private static final String DEFAULT_GROUP_NAME = "AxonFramework-Events";
    private Scheduler scheduler;
    private EventBus eventBus;
    private String groupIdentifier = DEFAULT_GROUP_NAME;
    private volatile boolean initialized;
    private UnitOfWorkFactory unitOfWorkFactory = new DefaultUnitOfWorkFactory();

    @Override
    public ScheduleToken schedule(DateTime triggerDateTime, Object event) {
        Assert.state(initialized, "Scheduler is not yet initialized");
        EventMessage eventMessage = GenericEventMessage.asEventMessage(event);
        String jobIdentifier = JOB_NAME_PREFIX + eventMessage.getIdentifier();
        QuartzScheduleToken tr = new QuartzScheduleToken(jobIdentifier, groupIdentifier);
        try {
            JobDetail jobDetail = buildJobDetail(eventMessage, new JobKey(jobIdentifier, groupIdentifier));
            scheduler.scheduleJob(jobDetail, buildTrigger(triggerDateTime, jobDetail.getKey()));
        } catch (SchedulerException e) {
            throw new SchedulingException("An error occurred while setting a timer for a saga", e);
        }
        return tr;
    }

    /**
     * Builds the JobDetail instance for Quartz, which defines the Job that needs to be executed when the trigger
     * fires.
     * <p/>
     * The resulting JobDetail must be identified by the given <code>jobKey</code> and represent a Job that dispatches
     * the given <code>event</code>.
     * <p/>
     * This method may be safely overridden to change behavior. Defaults to a JobDetail to fire a {@link FireEventJob}.
     *
     * @param event  The event to be scheduled for dispatch
     * @param jobKey The key of the Job to schedule
     * @return a JobDetail describing the Job to be executed
     */
    protected JobDetail buildJobDetail(EventMessage event, JobKey jobKey) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(FireEventJob.EVENT_KEY, event);
        return JobBuilder.newJob(FireEventJob.class)
                         .withDescription(event.getPayloadType().getName())
                         .withIdentity(jobKey)
                         .usingJobData(jobDataMap)
                         .build();
    }

    /**
     * Builds a Trigger which fires the Job identified by <code>jobKey</code> at (or around) the given
     * <code>triggerDateTime</code>.
     *
     * @param triggerDateTime The time at which a trigger was requested
     * @param jobKey          The key of the job to be triggered
     * @return a configured Trigger for the Job with key <code>jobKey</code>
     */
    protected Trigger buildTrigger(DateTime triggerDateTime, JobKey jobKey) {
        return TriggerBuilder.newTrigger()
                             .forJob(jobKey)
                             .startAt(triggerDateTime.toDate())
                             .build();
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        return schedule(new DateTime().plus(triggerDuration), event);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!QuartzScheduleToken.class.isInstance(scheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler.");
        }
        Assert.state(initialized, "Scheduler is not yet initialized");

        QuartzScheduleToken reference = (QuartzScheduleToken) scheduleToken;
        try {
            if (!scheduler.deleteJob(jobKey(reference.getJobIdentifier(), reference.getGroupIdentifier()))) {
                logger.warn("The job belonging to this token could not be deleted.");
            }
        } catch (SchedulerException e) {
            throw new SchedulingException("An error occurred while cancelling a timer for a saga", e);
        }
    }

    /**
     * Initializes the QuartzEventScheduler. Will make the configured Event Bus available to the Quartz Scheduler
     *
     * @throws SchedulerException if an error occurs preparing the Quartz Scheduler for use.
     */
    @PostConstruct
    public void initialize() throws SchedulerException {
        Assert.notNull(scheduler, "A Scheduler must be provided.");
        Assert.notNull(eventBus, "An EventBus must be provided.");
        scheduler.getContext().put(FireEventJob.EVENT_BUS_KEY, eventBus);
        scheduler.getContext().put(FireEventJob.UNIT_OF_WORK_FACTORY_KEY, unitOfWorkFactory);
        initialized = true;
    }

    /**
     * Sets the backing Quartz Scheduler for this timer.
     *
     * @param scheduler the backing Quartz Scheduler for this timer
     */
    @Resource
    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sets the event bus to which scheduled events need to be published.
     *
     * @param eventBus the event bus to which scheduled events need to be published.
     */
    @Resource
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Sets the group identifier to use when scheduling jobs with Quartz. Defaults to "AxonFramework-Events".
     *
     * @param groupIdentifier the group identifier to use when scheduling jobs with Quartz
     */
    public void setGroupIdentifier(String groupIdentifier) {
        this.groupIdentifier = groupIdentifier;
    }

    /**
     * Sets the transaction manager that manages a transaction around the publication of an event. Using this method
     * will configure a DefaultUnitOfWorkFactory. To use an alternative Unit of Work Factory, use {@link
     * #setUnitOfWorkFactory(org.axonframework.unitofwork.UnitOfWorkFactory)} which is configured with the proper
     * transaction manager.
     *
     * @param transactionManager the callback to invoke before and after publication of a scheduled event
     */
    public void setTransactionManager(TransactionManager transactionManager) {
        this.unitOfWorkFactory = new DefaultUnitOfWorkFactory(transactionManager);
    }

    /**
     * Sets the Unit of Work Factory instance which provides the UnitOfWork that manages the publication of the
     * scheduled event. Defaults to a DefaultUnitOfWorkFactory without a managed transaction, unless {@link
     * #setTransactionManager(org.axonframework.unitofwork.TransactionManager)} is used. In that case, a Transactional
     * instance of a DefaultUnitOfWorkFactory is used.
     *
     * @param unitOfWorkFactory The UnitOfWorkFactory that creates the Unit Of Work for the Event Publication
     */
    public void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory) {
        this.unitOfWorkFactory = unitOfWorkFactory;
    }
}

/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.EventTriggerCallback;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.ScheduledEvent;
import org.axonframework.eventhandling.scheduling.SchedulingException;
import org.axonframework.util.Assert;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * EventScheduler implementation that delegates scheduling and triggering to a Quartz Scheduler.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class QuartzEventScheduler extends AbstractQuartzEventScheduler {

    private static final Logger logger = LoggerFactory.getLogger(QuartzEventScheduler.class);
    private static final String JOB_NAME_PREFIX = "event-";
    private static final String DEFAULT_GROUP_NAME = "AxonFramework-Events";
    private Scheduler scheduler;
    private EventBus eventBus;
    private String groupIdentifier = DEFAULT_GROUP_NAME;
    private volatile boolean initialized;
    private EventTriggerCallback eventTriggerCallback;

    @Override
    public ScheduleToken schedule(DateTime triggerDateTime, ApplicationEvent event) {
        Assert.state(initialized, "Scheduler is not yet initialized");
        Object owner = event.getSource();
        String jobIdentifier = JOB_NAME_PREFIX + event.getEventIdentifier();
        QuartzScheduleToken tr = new QuartzScheduleToken(jobIdentifier, groupIdentifier);
        try {
            JobDetail jobDetail = new JobDetail(jobIdentifier, groupIdentifier, FireEventJob.class);
            jobDetail.getJobDataMap().put(Quartz2FireEventJob.EVENT_KEY, event);
            jobDetail.setDescription(String.format("%s, scheduled by %s.",
                                                   event.getClass().getName(),
                                                   owner.toString()));
            scheduler.scheduleJob(jobDetail, new SimpleTrigger(event.getEventIdentifier(),
                                                               triggerDateTime.toDate()));
        } catch (SchedulerException e) {
            throw new SchedulingException("An error occurred while setting a timer for a saga", e);
        }
        return tr;
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, ApplicationEvent event) {
        return schedule(new DateTime().plus(triggerDuration), event);
    }

    @Override
    public ScheduleToken schedule(ScheduledEvent event) {
        return schedule(event.getScheduledTime(), event);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!QuartzScheduleToken.class.isInstance(scheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler.");
        }
        Assert.state(initialized, "Scheduler is not yet initialized");

        QuartzScheduleToken reference = (QuartzScheduleToken) scheduleToken;
        try {
            if (!scheduler.deleteJob(reference.getJobIdentifier(), reference.getGroupIdentifier())) {
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
        scheduler.getContext().put(Quartz2FireEventJob.EVENT_BUS_KEY, eventBus);
        scheduler.getContext().put(Quartz2FireEventJob.TRIGGER_CALLBACK_KEY, eventTriggerCallback);
        initialized = true;
    }

    /**
     * Sets the backing Quartz Scheduler for this timer.
     *
     * @param scheduler the backing Quartz Scheduler for this timer
     */
    @Resource
    @Override
    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sets the event bus to which scheduled events need to be published.
     *
     * @param eventBus the event bus to which scheduled events need to be published.
     */
    @Resource
    @Override
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Sets the group identifier to use when scheduling jobs with Quartz. Defaults to "AxonFramework-Events".
     *
     * @param groupIdentifier the group identifier to use when scheduling jobs with Quartz
     */
    @Override
    public void setGroupIdentifier(String groupIdentifier) {
        this.groupIdentifier = groupIdentifier;
    }

    /**
     * Sets the callback to invoke before and after publication of a scheduled event.
     *
     * @param eventTriggerCallback the callback to invoke before and after publication of a scheduled event
     */
    @Override
    public void setEventTriggerCallback(EventTriggerCallback eventTriggerCallback) {
        this.eventTriggerCallback = eventTriggerCallback;
    }
}

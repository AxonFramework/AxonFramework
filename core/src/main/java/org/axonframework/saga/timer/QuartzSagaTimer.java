/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.saga.timer;

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.Saga;
import org.axonframework.util.Assert;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class QuartzSagaTimer implements SagaTimer {

    private Scheduler scheduler;
    private static final String JOB_NAME_PREFIX = "event-";
    private static final String GROUP_NAME_PREFIX = "saga-";
    private EventBus eventBus;

    @Override
    public TimerReference createTimer(DateTime triggerDateTime, ApplicationEvent event) {
        Saga owner = extractOwnerFrom(event);
        QuartzTimerReference tr = new QuartzTimerReference(event.getEventIdentifier().toString(),
                                                           owner.getSagaIdentifier());
        try {
            JobDetail jobDetail = new JobDetail(JOB_NAME_PREFIX + event.getEventIdentifier().toString(),
                                                GROUP_NAME_PREFIX + owner.getSagaIdentifier(),
                                                FireEventJob.class);
            jobDetail.setVolatility(false);
            jobDetail.getJobDataMap().put(FireEventJob.EVENT_KEY, event);
            jobDetail.setDescription(String.format("Timer scheduled by %s [%s]: %s",
                                                   owner.getClass().getName(),
                                                   owner.getSagaIdentifier(),
                                                   event.getClass().getName()));
            scheduler.scheduleJob(jobDetail, new SimpleTrigger(event.getEventIdentifier().toString(),
                                                               triggerDateTime.toDate()));
        } catch (SchedulerException e) {
            throw new TimerException("An error occurred while setting a timer for a saga", e);
        }
        return tr;
    }

    @Override
    public TimerReference createTimer(Duration triggerDuration, ApplicationEvent event) {
        return createTimer(new DateTime().plus(triggerDuration), event);
    }

    @Override
    public TimerReference createTimer(ScheduledEvent event) {
        return createTimer(event.getScheduledTime(), event);
    }

    @Override
    public void cancelTimer(TimerReference timerReference) {
        try {
            scheduler.deleteJob(JOB_NAME_PREFIX + timerReference.getIdentifier(),
                                GROUP_NAME_PREFIX + timerReference.getSagaIdentifier());
        } catch (SchedulerException e) {
            throw new TimerException("An error occurred while cancelling a timer for a saga", e);
        }
    }

    private Saga extractOwnerFrom(ApplicationEvent event) {
        Object source = event.getSource();
        if (source == null) {
            throw new IllegalArgumentException("The event's source may not be null. "
                    + "Make sure the event's source has been set and the event has not been serialized.");
        }
        if (!Saga.class.isInstance(source)) {
            throw new IllegalArgumentException("The event's source must be a Saga instance");
        }
        return Saga.class.cast(source);
    }

    /**
     * Initializes the SagaTimer. Will make the configured Event Bus available to the Quartz Scheduler
     *
     * @throws SchedulerException if an error occurs preparing the Quartz Scheduler for use.
     */
    @PostConstruct
    public void initialize() throws SchedulerException {
        Assert.notNull(scheduler, "A scheduler must be provided.");
        Assert.notNull(eventBus, "An event bus must be provided.");
        scheduler.getContext().put(FireEventJob.EVENT_BUS_KEY, eventBus);
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
}

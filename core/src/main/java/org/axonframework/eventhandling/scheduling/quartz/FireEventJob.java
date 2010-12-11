/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job that publishes an event on an event bus. The event is retrieved from the JobExecutionContext. The Event
 * Bus is fetched from the scheduler context.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class FireEventJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(FireEventJob.class);

    /**
     * The key used to locate the event in the JobExecutionContext.
     */
    public static final String EVENT_KEY = Event.class.getName();

    /**
     * The key used to locate the Event Bus in the scheduler context.
     */
    public static final String EVENT_BUS_KEY = EventBus.class.getName();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.debug("Starting job to publish a scheduled event");
        Event event = (Event) context.getJobDetail().getJobDataMap().get(EVENT_KEY);
        try {
            EventBus eventBus = (EventBus) context.getScheduler().getContext().get(EVENT_BUS_KEY);
            eventBus.publish(event);
            logger.info("Job successfully executed. Scheduled Event has been published.");
        } catch (SchedulerException e) {
            logger.warn("Exception occurred while executing a scheduled job");
            throw new JobExecutionException(e);
        }
    }
}

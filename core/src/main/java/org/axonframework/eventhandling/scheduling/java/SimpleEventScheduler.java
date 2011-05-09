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

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.domain.ApplicationEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.EventTriggerCallback;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.ScheduledEvent;
import org.axonframework.util.Assert;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An {@link EventScheduler} implementation that uses Java's ScheduledExecutorService as scheduling and triggering
 * mechanism.
 * <p/>
 * Note that this mechanism is non-persistent. Scheduled tasks will be lost when the JVM is shut down, unless special
 * measures have been taken to prevent that. For more flexible and powerful scheduling options, see {@link
 * org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler}.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler
 * @since 0.7
 */
public class SimpleEventScheduler implements EventScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventScheduler.class);

    private final ScheduledExecutorService executorService;
    private final EventBus eventBus;
    private final EventTriggerCallback eventTriggerCallback;

    /**
     * Initialize the SimpleEventScheduler using the given <code>executorService</code> as trigger and execution
     * mechanism, and publishes events to the given <code>eventBus</code>.
     *
     * @param executorService The backing ScheduledExecutorService
     * @param eventBus        The Event Bus on which Events are to be published
     */
    public SimpleEventScheduler(ScheduledExecutorService executorService, EventBus eventBus) {
        this(executorService, eventBus, new NoOpEventTriggerCallback());
    }

    /**
     * Initialize the SimpleEventScheduler using the given <code>executorService</code> as trigger and execution
     * mechanism, and publishes events to the given <code>eventBus</code>. The <code>eventTriggerCallback</code> is
     * invoked just before and after publication of a scheduled event.
     *
     * @param executorService      The backing ScheduledExecutorService
     * @param eventBus             The Event Bus on which Events are to be published
     * @param eventTriggerCallback To notify when an event is published
     */
    public SimpleEventScheduler(ScheduledExecutorService executorService, EventBus eventBus,
                                EventTriggerCallback eventTriggerCallback) {
        Assert.notNull(executorService, "The ScheduledExecutorService may not be null");
        Assert.notNull(eventBus, "The EventBus may not be null");

        this.executorService = executorService;
        this.eventBus = eventBus;
        this.eventTriggerCallback = eventTriggerCallback;
    }

    @Override
    public ScheduleToken schedule(DateTime triggerDateTime, ApplicationEvent event) {
        return schedule(new Duration(null, triggerDateTime), event);
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, ApplicationEvent event) {
        ScheduledFuture<?> future = executorService.schedule(new PublishEventTask(event),
                                                             triggerDuration.getMillis(),
                                                             TimeUnit.MILLISECONDS);
        return new SimpleScheduleToken(future);
    }

    @Override
    public ScheduleToken schedule(ScheduledEvent event) {
        return schedule(event.getScheduledTime(), event);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!SimpleScheduleToken.class.isInstance(scheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler.");
        }
        SimpleScheduleToken token = (SimpleScheduleToken) scheduleToken;
        token.getFuture().cancel(false);
    }

    private class PublishEventTask implements Runnable {

        private final ApplicationEvent event;

        public PublishEventTask(ApplicationEvent event) {
            this.event = event;
        }

        @Override
        public void run() {
            eventTriggerCallback.beforePublication(event);
            try {
                eventBus.publish(event);
            } catch (RuntimeException e) {
                eventTriggerCallback.afterPublicationFailure(e);
                throw e;
            }
            eventTriggerCallback.afterPublicationSuccess();
        }
    }

    private static class NoOpEventTriggerCallback implements EventTriggerCallback {

        @Override
        public void beforePublication(ApplicationEvent event) {
        }

        @Override
        public void afterPublicationSuccess() {
        }

        @Override
        public void afterPublicationFailure(RuntimeException cause) {
            logger.error("An exception occurred while processing a scheduled Event.", cause);
        }
    }
}

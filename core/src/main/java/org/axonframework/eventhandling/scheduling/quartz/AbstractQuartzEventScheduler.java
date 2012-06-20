package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.EventTriggerCallback;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * Abstract class to provide a single API for both Quartz 1 and Quartz 2 based schedulers, to allow the
 * QuartzEventSchedulerFactoryBean to automatically use the correct implementation, based on the Quartz version on the
 * classpath.
 *
 * @author Allard Buijze
 * @since 1.4
 */
public abstract class AbstractQuartzEventScheduler implements EventScheduler {

    /**
     * Initializes the Quartz2EventScheduler. Will make the configured Event Bus available to the Quartz Scheduler
     *
     * @throws org.quartz.SchedulerException if an error occurs preparing the Quartz Scheduler for use.
     */
    @PostConstruct
    public abstract void initialize() throws SchedulerException;

    /**
     * Sets the backing Quartz Scheduler for this timer.
     *
     * @param scheduler the backing Quartz Scheduler for this timer
     */
    @Resource
    public abstract void setScheduler(Scheduler scheduler);

    /**
     * Sets the event bus to which scheduled events need to be published.
     *
     * @param eventBus the event bus to which scheduled events need to be published.
     */
    @Resource
    public abstract void setEventBus(EventBus eventBus);

    /**
     * Sets the group identifier to use when scheduling jobs with Quartz. Defaults to "AxonFramework-Events".
     *
     * @param groupIdentifier the group identifier to use when scheduling jobs with Quartz
     */
    public abstract void setGroupIdentifier(String groupIdentifier);

    /**
     * Sets the callback to invoke before and after publication of a scheduled event.
     *
     * @param eventTriggerCallback the callback to invoke before and after publication of a scheduled event
     */
    public abstract void setEventTriggerCallback(EventTriggerCallback eventTriggerCallback);
}

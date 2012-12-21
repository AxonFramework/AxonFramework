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

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.TransactionManager;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job that publishes an event on an Event Bus. The event is retrieved from the JobExecutionContext. The Event
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
    public static final String EVENT_KEY = EventMessage.class.getName();

    /**
     * The key used to locate the Event Bus in the scheduler context.
     */
    public static final String EVENT_BUS_KEY = EventBus.class.getName();
    /**
     * The key used to locate the optional EventTriggerCallback in the scheduler context.
     */
    public static final String UNIT_OF_WORK_FACTORY_KEY = TransactionManager.class.getName();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.debug("Starting job to publish a scheduled event");
        Object event = context.getJobDetail().getJobDataMap().get(EVENT_KEY);
        EventMessage<?> eventMessage = createMessage(event);
        try {
            EventBus eventBus = (EventBus) context.getScheduler().getContext().get(EVENT_BUS_KEY);
            UnitOfWorkFactory unitOfWorkFactory =
                    (UnitOfWorkFactory) context.getScheduler().getContext().get(UNIT_OF_WORK_FACTORY_KEY);
            UnitOfWork uow = unitOfWorkFactory.createUnitOfWork();
            try {
                uow.publishEvent(eventMessage, eventBus);
            } finally {
                uow.commit();
            }
            if (logger.isInfoEnabled()) {
                logger.info("Job successfully executed. Scheduled Event [{}] has been published.",
                            eventMessage.getPayloadType().getSimpleName());
            }
        } catch (Exception e) {
            logger.warn("Exception occurred while publishing scheduled event [{}]",
                        eventMessage.getPayloadType().getSimpleName());
            throw new JobExecutionException(e);
        }
    }

    /**
     * Creates a new message for the scheduled event. This ensures that a new identifier and timestamp will always
     * be generated, so that the timestamp will reflect the actual moment the trigger occurred.
     *
     * @param event The actual event (either a payload or an entire message) to create the message from
     * @return the message to publish
     */
    private EventMessage<?> createMessage(Object event) {
        EventMessage<?> eventMessage;
        if (event instanceof EventMessage) {
            eventMessage = new GenericEventMessage<Object>(((EventMessage) event).getPayload(),
                                                           ((EventMessage) event).getMetaData());
        } else {
            eventMessage = new GenericEventMessage<Object>(event);
        }
        return eventMessage;
    }
}

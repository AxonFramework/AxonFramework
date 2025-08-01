/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job that publishes an event on an Event Bus. The event is retrieved from the JobExecutionContexts
 * {@link org.quartz.JobDataMap}. Both the {@link EventBus} and {@link EventJobDataBinder} are fetched from the
 * scheduler context.
 *
 * @author Allard Buijze
 * @see EventJobDataBinder
 * @since 0.7
 */
public class FireEventJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(FireEventJob.class);

    /**
     * The key used to locate the {@link EventJobDataBinder} in the scheduler context.
     */
    public static final String EVENT_JOB_DATA_BINDER_KEY = EventJobDataBinder.class.getName();

    /**
     * The key used to locate the Event Bus in the scheduler context.
     */
    public static final String EVENT_BUS_KEY = EventBus.class.getName();

    /**
     * The key used to locate the optional TransactionManager in the scheduler context.
     */
    public static final String TRANSACTION_MANAGER_KEY = TransactionManager.class.getName();

    /**
     * The key used to locate the MessageNameResolver in the scheduler context.
     */
    public static final String MESSAGE_TYPE_RESOLVER_KEY = MessageTypeResolver.class.getName();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.debug("Starting job to publish a scheduled event");

        JobDetail jobDetail = context.getJobDetail();
        JobDataMap jobData = jobDetail.getJobDataMap();

        try {
            SchedulerContext schedulerContext = context.getScheduler().getContext();

            EventJobDataBinder jobDataBinder = (EventJobDataBinder) schedulerContext.get(EVENT_JOB_DATA_BINDER_KEY);
            Object event = jobDataBinder.fromJobData(jobData);
            MessageTypeResolver messageTypeResolver = (MessageTypeResolver) schedulerContext.get(
                    MESSAGE_TYPE_RESOLVER_KEY);
            EventMessage<?> eventMessage = createMessage(event, messageTypeResolver);

            EventBus eventBus = (EventBus) schedulerContext.get(EVENT_BUS_KEY);
            TransactionManager txManager = (TransactionManager) schedulerContext.get(TRANSACTION_MANAGER_KEY);

            LegacyUnitOfWork<EventMessage<?>> unitOfWork = LegacyDefaultUnitOfWork.startAndGet(null);
            if (txManager != null) {
                unitOfWork.attachTransaction(txManager);
            }
            unitOfWork.execute((ctx) -> eventBus.publish(eventMessage));

            if (logger.isInfoEnabled()) {
                logger.info("Job successfully executed. Scheduled Event [{}] has been published.",
                            eventMessage.getPayloadType().getSimpleName());
            }
        } catch (Exception e) {
            logger.error("Exception occurred while publishing scheduled event [{}]", jobDetail.getDescription(), e);
            throw new JobExecutionException(e);
        }
    }

    /**
     * Creates a new message for the scheduled event. This ensures that a new identifier and timestamp will always be
     * generated, so that the timestamp will reflect the actual moment the trigger occurred.
     *
     * @param event The actual event (either a payload or an entire message) to create the message from
     * @param messageTypeResolver used to resolve the {@link QualifiedName} when publishing {@link EventMessage EventMessages}.
     * @return the message to publish
     */
    private EventMessage<?> createMessage(Object event, MessageTypeResolver messageTypeResolver) {
        return event instanceof EventMessage
                ? new GenericEventMessage<>(((EventMessage<?>) event).type(),
                                            ((EventMessage<?>) event).payload(),
                                            ((EventMessage<?>) event).getMetaData())
                : new GenericEventMessage<>(messageTypeResolver.resolveOrThrow(event), event);
    }
}

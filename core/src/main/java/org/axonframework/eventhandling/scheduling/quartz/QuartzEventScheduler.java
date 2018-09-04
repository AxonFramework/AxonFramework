/*
 * Copyright (c) 2010-2014. Axon Framework
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
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.SchedulingException;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.xml.XStreamSerializer;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.annotation.PostConstruct;

import static org.axonframework.common.Assert.notNull;
import static org.axonframework.eventhandling.scheduling.quartz.FireEventJob.*;
import static org.axonframework.messaging.Headers.*;
import static org.quartz.JobKey.jobKey;

/**
 * EventScheduler implementation that delegates scheduling and triggering to a Quartz Scheduler.
 *
 * @author Allard Buijze
 * @see EventJobDataBinder
 * @see FireEventJob
 * @since 0.7
 */
public class QuartzEventScheduler implements org.axonframework.eventhandling.scheduling.EventScheduler {

    private static final Logger logger = LoggerFactory.getLogger(QuartzEventScheduler.class);
    private static final String JOB_NAME_PREFIX = "event-";
    private static final String DEFAULT_GROUP_NAME = "AxonFramework-Events";

    private Scheduler scheduler;
    private EventBus eventBus;
    private EventJobDataBinder jobDataBinder = new DirectEventJobDataBinder();
    private TransactionManager transactionManager = NoTransactionManager.INSTANCE;

    private String groupIdentifier = DEFAULT_GROUP_NAME;
    private volatile boolean initialized;

    @Override
    public ScheduleToken schedule(Instant triggerDateTime, Object event) {
        Assert.state(initialized, () -> "Scheduler is not yet initialized");
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
     * The resulting JobDetail must be identified by the given {@code jobKey} and represent a Job that dispatches
     * the given {@code event}.
     * <p/>
     * This method may be safely overridden to change behavior. Defaults to a JobDetail to fire a {@link FireEventJob}.
     *
     * @param event  The event to be scheduled for dispatch
     * @param jobKey The key of the Job to schedule
     * @return a JobDetail describing the Job to be executed
     */
    protected JobDetail buildJobDetail(EventMessage event, JobKey jobKey) {
        JobDataMap jobData = jobDataBinder.toJobData(event);
        return JobBuilder.newJob(FireEventJob.class)
                         .withDescription(event.getPayloadType().getName())
                         .withIdentity(jobKey)
                         .usingJobData(jobData)
                         .build();
    }

    /**
     * Builds a Trigger which fires the Job identified by {@code jobKey} at (or around) the given
     * {@code triggerDateTime}.
     *
     * @param triggerDateTime The time at which a trigger was requested
     * @param jobKey          The key of the job to be triggered
     * @return a configured Trigger for the Job with key {@code jobKey}
     */
    protected Trigger buildTrigger(Instant triggerDateTime, JobKey jobKey) {
        return TriggerBuilder.newTrigger()
                             .forJob(jobKey)
                             .startAt(Date.from(triggerDateTime))
                             .build();
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        return schedule(Instant.now().plus(triggerDuration), event);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!(scheduleToken instanceof QuartzScheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler.");
        }
        Assert.state(initialized, () -> "Scheduler is not yet initialized");

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
     * Initializes the QuartzEventScheduler. Makes the configured {@link EventBus} available to the Quartz Scheduler.
     *
     * @throws SchedulerException if an error occurs preparing the Quartz Scheduler for use.
     */
    @PostConstruct
    public void initialize() throws SchedulerException {
        notNull(scheduler, () -> "A Scheduler must be provided.");
        notNull(eventBus, () -> "An EventBus must be provided.");
        notNull(jobDataBinder, () -> "An EventJobDataBinder must be provided.");

        scheduler.getContext().put(EVENT_BUS_KEY, eventBus);
        scheduler.getContext().put(TRANSACTION_MANAGER_KEY, transactionManager);
        scheduler.getContext().put(EVENT_JOB_DATA_BINDER_KEY, jobDataBinder);

        initialized = true;
    }

    /**
     * Sets the backing Quartz Scheduler for this timer.
     *
     * @param scheduler the backing Quartz Scheduler for this timer
     */
    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sets the event bus to which scheduled events need to be published.
     *
     * @param eventBus the event bus to which scheduled events need to be published.
     */
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
     * Sets the transaction manager that manages a transaction around the publication of an event.
     *
     * @param transactionManager the callback to invoke before and after publication of a scheduled event
     */
    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Sets the {@link EventJobDataBinder} instance which reads / writes the event message to publish to the
     * {@link JobDataMap}. Defaults to {@link DirectEventJobDataBinder}.
     *
     * @param jobDataBinder to use
     */
    public void setEventJobDataBinder(EventJobDataBinder jobDataBinder) {
        this.jobDataBinder = jobDataBinder;
    }

    /**
     * Binds an {@link EventMessage} to the {@link JobDataMap} by serializing the payload and metadata with a
     * {@link Serializer}. All the important EventMessage fields, thus the message identifier, timestamp, and the
     * serialized payload and metadata, are stored as separate values in the JobDataMap.
     * <p>
     * The old approach, which let Quartz do the serialization of the entire EventMessage at once, is maintained for
     * backwards compatibility only.
     */
    public static class DirectEventJobDataBinder implements EventJobDataBinder {

        /**
         * The key used to locate the event in the {@link JobDataMap}.
         *
         * @deprecated in favor of the default message keys | only maintained for backwards compatibility
         */
        @Deprecated
        public static final String EVENT_KEY = EventMessage.class.getName();

        private final Serializer serializer;

        /**
         * Instantiate a {@link DirectEventJobDataBinder} which defaults to a {@link XStreamSerializer} for
         * de-/serializing event messages.
         */
        public DirectEventJobDataBinder() {
            this(new XStreamSerializer());
        }

        /**
         * Instantiate a {@link DirectEventJobDataBinder} with the provided {@link Serializer} for
         * de-/serializing event messages.
         *
         * @param serializer the {@link Serializer} used for de-/serializing event messages
         */
        public DirectEventJobDataBinder(Serializer serializer) {
            this.serializer = serializer;
        }

        @Override
        public JobDataMap toJobData(Object event) {
            JobDataMap jobData = new JobDataMap();

            EventMessage eventMessage = (EventMessage) event;

            jobData.put(MESSAGE_ID, eventMessage.getIdentifier());
            jobData.put(MESSAGE_TIMESTAMP, eventMessage.getTimestamp().toEpochMilli());

            SerializedObject<byte[]> serializedPayload =
                    serializer.serialize(eventMessage.getPayload(), byte[].class);
            jobData.put(SERIALIZED_MESSAGE_PAYLOAD, serializedPayload.getData());
            jobData.put(MESSAGE_TYPE, serializedPayload.getType().getName());
            jobData.put(MESSAGE_REVISION, serializedPayload.getType().getRevision());

            SerializedObject<byte[]> serializedMetaData =
                    serializer.serialize(eventMessage.getMetaData(), byte[].class);
            jobData.put(MESSAGE_METADATA, serializedMetaData.getData());

            return jobData;
        }

        /**
         * <b>Note</b> that this function is able to retrieve an Event Message in two formats. The first is the
         * original, deprecated approach, which uses the Quartz serializer (read Java serialization) to serialize an
         * entire event message at once. The second approach uses the configurable {@link Serializer} in this class to
         * serialized the payload and metadata of an event message. All fields which required to instantiate a
         * {@link GenericEventMessage} are currently stored in the {@code jobDataMap} when calling the
         * {@link DirectEventJobDataBinder#toJobData(Object)} function.
         * Approach one only exists for backwards compatibility and should be removed in subsequent major releases.
         * <p>
         * {@inheritDoc}
         */
        @Override
        public Object fromJobData(JobDataMap jobDataMap) {
            if (jobDataMap.containsKey(SERIALIZED_MESSAGE_PAYLOAD)) {
                return new GenericEventMessage<>((String) jobDataMap.get(MESSAGE_ID),
                                                 deserializePayload(jobDataMap),
                                                 deserializeMetaData(jobDataMap),
                                                 retrieveDeadlineTimestamp(jobDataMap));
            }

            return fromJobDataMap(jobDataMap);
        }

        /**
         * @deprecated this private function is in place for backwards compatibility only. Ideally, the event message is
         * retrieved based on the serialized keys.
         */
        @Deprecated
        private Object fromJobDataMap(JobDataMap jobDataMap) {
            return jobDataMap.get(EVENT_KEY);
        }

        private Object deserializePayload(JobDataMap jobDataMap) {
            SimpleSerializedObject<byte[]> serializedPayload = new SimpleSerializedObject<>(
                    (byte[]) jobDataMap.get(SERIALIZED_MESSAGE_PAYLOAD),
                    byte[].class,
                    (String) jobDataMap.get(MESSAGE_TYPE),
                    (String) jobDataMap.get(MESSAGE_REVISION)
            );
            return serializer.deserialize(serializedPayload);
        }

        private Map<String, ?> deserializeMetaData(JobDataMap jobDataMap) {
            SimpleSerializedObject<byte[]> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                    (byte[]) jobDataMap.get(MESSAGE_METADATA), byte[].class, MetaData.class.getName(), null
            );
            return serializer.deserialize(serializedDeadlineMetaData);
        }

        private Instant retrieveDeadlineTimestamp(JobDataMap jobDataMap) {
            return Instant.ofEpochMilli((long) jobDataMap.get(MESSAGE_TIMESTAMP));
        }
    }
}

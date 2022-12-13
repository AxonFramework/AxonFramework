/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.scheduling.jobrunr;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.SchedulingException;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nonnull;

import static java.util.Objects.isNull;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.eventhandling.GenericEventMessage.clock;

/**
 * EventScheduler implementation that delegates scheduling and triggering to a JobRunr JobScheduler.
 *
 * @author Gerard Klijs
 * @since 4.7.0
 */
public class JobRunrEventScheduler implements EventScheduler, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(JobRunrEventScheduler.class);
    private final JobScheduler jobScheduler;
    private final Serializer serializer;
    private final TransactionManager transactionManager;
    private final EventBus eventBus;

    /**
     * Instantiate a {@link JobRunrEventScheduler} based on the fields contained in the
     * {@link JobRunrEventScheduler.Builder}.
     * <p>
     * Will assert that the {@link JobScheduler}, {@link Serializer} and {@link EventBus} are not {@code null}, and will
     * throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link JobRunrEventScheduler} instance
     */
    protected JobRunrEventScheduler(Builder builder) {
        builder.validate();
        jobScheduler = builder.jobScheduler;
        serializer = builder.serializer;
        transactionManager = builder.transactionManager;
        eventBus = builder.eventBus;
    }

    /**
     * Instantiate a Builder to be able to create a {@link JobRunrEventScheduler}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link JobScheduler}, {@link Serializer} and {@link EventBus} are <b>hard requirements</b> and as such should
     * be provided.
     *
     * @return a Builder to be able to create a {@link JobRunrEventScheduler}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ScheduleToken schedule(Instant triggerDateTime, Object event) {
        UUID jobIdentifier = UUID.randomUUID();
        try {
            if (event instanceof EventMessage) {
                schedulePayloadAndMetadata(jobIdentifier, triggerDateTime, (EventMessage) event);
            } else {
                schedulePayload(jobIdentifier, triggerDateTime, event);
            }
        } catch (Exception e) {
            throw new SchedulingException("An error occurred while scheduling an event.", e);
        }
        return new JobRunrScheduleToken(jobIdentifier);
    }

    private void schedulePayload(UUID jobIdentifier, Instant triggerDateTime, Object event) {
        SerializedObject<String> serialized = serializer.serialize(event, String.class);
        String serializedPayload = serialized.getData();
        String payloadClass = serialized.getType().getName();
        String revision = serialized.getType().getRevision();
        if (isNull(revision)) {
            jobScheduler.<JobRunrEventScheduler>schedule(
                    jobIdentifier,
                    triggerDateTime,
                    eventScheduler -> eventScheduler.publish(serializedPayload, payloadClass)
            );
        } else {
            jobScheduler.<JobRunrEventScheduler>schedule(
                    jobIdentifier,
                    triggerDateTime,
                    eventScheduler -> eventScheduler.publishWithRevision(serializedPayload, payloadClass, revision)
            );
        }
    }

    @SuppressWarnings("rawtypes")
    private void schedulePayloadAndMetadata(UUID jobIdentifier, Instant triggerDateTime, EventMessage eventMessage) {
        SerializedObject<String> serialized = serializer.serialize(eventMessage.getPayload(), String.class);
        String serializedPayload = serialized.getData();
        String payloadClass = serialized.getType().getName();
        String revision = serialized.getType().getRevision();
        String serializedMetadata = serializer.serialize(eventMessage.getMetaData(), String.class).getData();
        if (isNull(revision)) {
            jobScheduler.<JobRunrEventScheduler>schedule(
                    jobIdentifier,
                    triggerDateTime,
                    eventScheduler -> eventScheduler.publish(serializedPayload, payloadClass, serializedMetadata)
            );
        } else {
            jobScheduler.<JobRunrEventScheduler>schedule(
                    jobIdentifier,
                    triggerDateTime,
                    eventScheduler -> eventScheduler.publishWithRevision(serializedPayload,
                                                                         payloadClass,
                                                                         revision,
                                                                         serializedMetadata)
            );
        }
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        return schedule(clock.instant().plus(triggerDuration), event);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!(scheduleToken instanceof JobRunrScheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler.");
        }
        JobRunrScheduleToken reference = (JobRunrScheduleToken) scheduleToken;
        jobScheduler.delete(reference.getJobIdentifier(), "Deleted via EventScheduler API");
    }

    @Override
    public void shutdown() {
        jobScheduler.shutdown();
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS, this::shutdown);
    }

    /**
     * This function should only be called via Jobrunr when a scheduled event was triggered. It will create an
     * {@link EventMessage} and publish it using the {@link EventBus}.
     *
     * @param serializedPayload The {@link String} with the serialized payload.
     * @param payloadClass      The {@link String} with the name of the class ot the payload.
     */
    public void publish(String serializedPayload, String payloadClass) {
        logger.debug("Invoked by JobRunr to publish a scheduled event without metadata.");
        EventMessage<?> eventMessage = createMessage(serializedPayload, payloadClass, null);
        publishEventMessage(eventMessage);
    }

    /**
     * This function should only be called via Jobrunr when a scheduled event was triggered. It will create an
     * {@link EventMessage} and publish it using the {@link EventBus}.
     *
     * @param serializedPayload  The {@link String} with the serialized payload.
     * @param payloadClass       The {@link String} with the name of the class of the payload.
     * @param serializedMetadata The {@link String} with the serialized metadata.
     */
    public void publish(String serializedPayload, String payloadClass, String serializedMetadata) {
        logger.debug("Invoked by JobRunr to publish a scheduled event with metadata");
        EventMessage<?> eventMessage = createMessage(serializedPayload, payloadClass, null, serializedMetadata);
        publishEventMessage(eventMessage);
    }

    /**
     * This function should only be called via Jobrunr when a scheduled event was triggered. It will create an
     * {@link EventMessage} and publish it using the {@link EventBus}.
     *
     * @param serializedPayload The {@link String} with the serialized payload.
     * @param payloadClass      The {@link String} with the name of the class ot the payload.
     * @param revision          The {@link String} with the revision of the class of the payload. Might be null.
     */
    public void publishWithRevision(String serializedPayload, String payloadClass, String revision) {
        logger.debug("Invoked by JobRunr to publish a scheduled event without metadata.");
        EventMessage<?> eventMessage = createMessage(serializedPayload, payloadClass, revision);
        publishEventMessage(eventMessage);
    }

    /**
     * This function should only be called via Jobrunr when a scheduled event was triggered. It will create an
     * {@link EventMessage} and publish it using the {@link EventBus}.
     *
     * @param serializedPayload  The {@link String} with the serialized payload.
     * @param payloadClass       The {@link String} with the name of the class of the payload.
     * @param revision           The {@link String} with the revision of the class of the payload. Might be null.
     * @param serializedMetadata The {@link String} with the serialized metadata.
     */
    public void publishWithRevision(String serializedPayload, String payloadClass, String revision,
                                    String serializedMetadata) {
        logger.debug("Invoked by JobRunr to publish a scheduled event with metadata");
        EventMessage<?> eventMessage = createMessage(serializedPayload, payloadClass, revision, serializedMetadata);
        publishEventMessage(eventMessage);
    }

    private EventMessage<?> createMessage(
            String serializedPayload,
            String payloadClass,
            String revision) {
        SimpleSerializedObject<String> serializedObject = new SimpleSerializedObject<>(
                serializedPayload, String.class, payloadClass, revision
        );
        Object deserializedPayload = serializer.deserialize(serializedObject);
        return GenericEventMessage.asEventMessage(deserializedPayload);
    }

    private EventMessage<?> createMessage(
            String serializedPayload,
            String payloadClass,
            String revision,
            String serializedMetadata) {
        EventMessage<?> eventMessage = createMessage(serializedPayload, payloadClass, revision);
        SimpleSerializedObject<String> serializedMetaData = new SimpleSerializedObject<>(
                serializedMetadata, String.class, MetaData.class.getName(), null
        );
        return eventMessage.andMetaData(serializer.deserialize(serializedMetaData));
    }

    @SuppressWarnings("rawtypes")
    private void publishEventMessage(EventMessage eventMessage) {
        UnitOfWork<EventMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        unitOfWork.attachTransaction(transactionManager);
        unitOfWork.execute(() -> eventBus.publish(eventMessage));
    }

    /**
     * Builder class to instantiate a {@link JobRunrEventScheduler}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link JobScheduler}, {@link Serializer} and {@link EventBus} are <b>hard requirements</b> and as such should
     * be provided.
     */
    public static class Builder {

        private JobScheduler jobScheduler;
        private Serializer serializer;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private EventBus eventBus;

        /**
         * Sets the {@link JobScheduler} used for scheduling and triggering purposes of the events.
         *
         * @param jobScheduler a {@link JobScheduler} used for scheduling and triggering purposes of the events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder jobScheduler(JobScheduler jobScheduler) {
            assertNonNull(jobScheduler, "JobScheduler may not be null");
            this.jobScheduler = jobScheduler;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@code payload} and the
         * {@link org.axonframework.messaging.MetaData}.
         *
         * @param serializer a {@link Serializer} used to de-/serialize the {@code payload}, and
         *                   {@link org.axonframework.messaging.MetaData}.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to build transactions and ties them on event publication. Defaults
         * to a {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to build transactions and ties them on event
         *                           publication
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link EventBus} used to publish events on once the schedule has been met.
         *
         * @param eventBus a {@link EventBus} used to publish events on once the schedule has been met
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventBus(EventBus eventBus) {
            assertNonNull(eventBus, "EventBus may not be null");
            this.eventBus = eventBus;
            return this;
        }

        /**
         * Initializes a {@link JobRunrEventScheduler} as specified through this Builder.
         *
         * @return a {@link JobRunrEventScheduler} as specified through this Builder
         */
        public JobRunrEventScheduler build() {
            return new JobRunrEventScheduler(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(jobScheduler, "The JobScheduler is a hard requirement and should be provided.");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided.");
            assertNonNull(eventBus, "The EventBus is a hard requirement and should be provided.");
        }
    }
}

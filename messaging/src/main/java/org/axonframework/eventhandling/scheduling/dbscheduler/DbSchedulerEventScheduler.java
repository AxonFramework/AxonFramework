/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.scheduling.dbscheduler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

import static java.util.Objects.isNull;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.eventhandling.GenericEventMessage.clock;
import static org.axonframework.eventhandling.scheduling.dbscheduler.DbSchedulerScheduleToken.TASK_NAME;

/**
 * EventScheduler implementation that delegates scheduling and triggering to a db-scheduler Scheduler.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
public class DbSchedulerEventScheduler implements EventScheduler, Lifecycle {

    private static final AtomicReference<DbSchedulerEventScheduler> eventSchedulerReference = new AtomicReference<>();
    private final Scheduler scheduler;
    private final Serializer serializer;
    private final TransactionManager transactionManager;
    private final EventBus eventBus;

    /**
     * Instantiate a {@link DbSchedulerEventScheduler} based on the fields contained in the
     * {@link DbSchedulerEventScheduler.Builder}.
     * <p>
     * Will assert that the {@link Scheduler}, {@link Serializer} and {@link EventBus} are not {@code null}, and will
     * throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DbSchedulerEventScheduler} instance
     */
    protected DbSchedulerEventScheduler(Builder builder) {
        builder.validate();
        scheduler = builder.scheduler;
        serializer = builder.serializer;
        transactionManager = builder.transactionManager;
        eventBus = builder.eventBus;
        eventSchedulerReference.set(this);
    }

    /**
     * Instantiate a Builder to be able to create a {@link DbSchedulerEventScheduler}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link Scheduler}, {@link Serializer} and {@link EventBus} are <b>hard requirements</b> and as such should be
     * provided.
     *
     * @return a Builder to be able to create a {@link DbSchedulerEventScheduler}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ScheduleToken schedule(Instant triggerDateTime, Object event) {
        DbSchedulerScheduleToken taskInstanceId = new DbSchedulerScheduleToken(UUID.randomUUID().toString());
        try {
            DbSchedulerEventData data;
            if (event instanceof EventMessage) {
                data = detailsFromEvent((EventMessage<?>) event);
            } else {
                data = detailsFromObject(event);
            }
            TaskInstance<?> taskInstance = task().instance(taskInstanceId.getId(), data);
            scheduler.schedule(taskInstance, triggerDateTime);
        } catch (Exception e) {
            throw new SchedulingException("An error occurred while scheduling an event.", e);
        }
        return taskInstanceId;
    }

    @SuppressWarnings("raw")
    public static Task<DbSchedulerEventData> task() {
        return new Tasks.OneTimeTaskBuilder<>(TASK_NAME, DbSchedulerEventData.class)
                .execute((ti, context) -> {
                    DbSchedulerEventScheduler eventScheduler = eventSchedulerReference.get();
                    if (isNull(eventScheduler)) {
                        throw new EventSchedulerNotSetException();
                    }
                    EventMessage<?> eventMessage = eventScheduler.fromDbSchedulerEventData(ti.getData());
                    eventScheduler.publishEventMessage(eventMessage);
                });
    }

    private DbSchedulerEventData detailsFromObject(Object event) {
        SerializedObject<String> serialized = serializer.serialize(event, String.class);
        String serializedPayload = serialized.getData();
        String payloadClass = serialized.getType().getName();
        String revision = serialized.getType().getRevision();
        return new DbSchedulerEventData(serializedPayload, payloadClass, revision, null);
    }

    private DbSchedulerEventData detailsFromEvent(EventMessage<?> eventMessage) {
        SerializedObject<String> serialized = serializer.serialize(eventMessage.getPayload(), String.class);
        String serializedPayload = serialized.getData();
        String payloadClass = serialized.getType().getName();
        String revision = serialized.getType().getRevision();
        String serializedMetadata = serializer.serialize(eventMessage.getMetaData(), String.class).getData();
        return new DbSchedulerEventData(serializedPayload, payloadClass, revision, serializedMetadata);
    }

    private EventMessage<?> fromDbSchedulerEventData(DbSchedulerEventData data) {
        SimpleSerializedObject<String> serializedObject = new SimpleSerializedObject<>(
                data.getSerializedPayload(), String.class, data.getPayloadClass(), data.getRevision()
        );
        Object deserializedPayload = serializer.deserialize(serializedObject);
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(deserializedPayload);
        if (!isNull(data.getSerializedMetadata())) {
            SimpleSerializedObject<String> serializedMetaData = new SimpleSerializedObject<>(
                    data.getSerializedMetadata(), String.class, MetaData.class.getName(), null
            );
            eventMessage = eventMessage.andMetaData(serializer.deserialize(serializedMetaData));
        }
        return eventMessage;
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        return schedule(clock.instant().plus(triggerDuration), event);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!(scheduleToken instanceof DbSchedulerScheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler.");
        }
        DbSchedulerScheduleToken reference = (DbSchedulerScheduleToken) scheduleToken;
        scheduler.cancel(reference);
    }

    @Override
    public void shutdown() {
        scheduler.stop();
        eventSchedulerReference.set(null);
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS, this::shutdown);
    }

    @SuppressWarnings("rawtypes")
    private void publishEventMessage(EventMessage eventMessage) {
        UnitOfWork<EventMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        unitOfWork.attachTransaction(transactionManager);
        unitOfWork.execute(() -> eventBus.publish(eventMessage));
    }

    /**
     * Builder class to instantiate a {@link DbSchedulerEventScheduler}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link Scheduler}, {@link Serializer} and {@link EventBus} are <b>hard requirements</b> and as such should be
     * provided.
     */
    public static class Builder {

        private Scheduler scheduler;
        private Serializer serializer;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private EventBus eventBus;

        /**
         * Sets the {@link Scheduler} used for scheduling and triggering purposes of the events.
         *
         * @param scheduler a {@link Scheduler} used for scheduling and triggering purposes of the events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scheduler(Scheduler scheduler) {
            assertNonNull(scheduler, "Scheduler may not be null");
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@code payload} and the {@link MetaData}.
         *
         * @param serializer a {@link Serializer} used to de-/serialize the {@code payload}, and {@link MetaData}.
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
         * Initializes a {@link DbSchedulerEventScheduler} as specified through this Builder.
         *
         * @return a {@link DbSchedulerEventScheduler} as specified through this Builder
         */
        public DbSchedulerEventScheduler build() {
            return new DbSchedulerEventScheduler(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(scheduler, "The Scheduler is a hard requirement and should be provided.");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided.");
            assertNonNull(eventBus, "The EventBus is a hard requirement and should be provided.");
        }
    }
}

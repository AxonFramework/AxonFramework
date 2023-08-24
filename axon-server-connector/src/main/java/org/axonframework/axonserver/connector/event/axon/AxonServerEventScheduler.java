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

package org.axonframework.axonserver.connector.event.axon;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.grpc.InstructionAck;
import io.axoniq.axonserver.grpc.event.Event;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.event.util.GrpcExceptionParser;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.StringUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Implementation of the {@link EventScheduler} that uses Axon Server to schedule the events.
 *
 * @author Marc Gathier
 * @since 4.4
 */
public class AxonServerEventScheduler implements EventScheduler, Lifecycle {

    private final long requestTimeout;
    private final Serializer serializer;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private final GrpcMetaDataConverter converter;
    private final String context;

    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * Instantiate a Builder to be able to create a {@link AxonServerEventScheduler}.
     * <p>
     * The {@code requestTimeout} is defaulted to {@code 15000} millis.
     * <p>
     * The {@link Serializer} and {@link AxonServerConnectionManager} are <b>hard requirements</b> and as such
     * should be provided.
     *
     * @return a Builder to be able to create a {@link AxonServerEventScheduler}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiates an {@link AxonServerEventScheduler} using the given {@link Builder}.
     * <p>
     * Will assert that the {@link Serializer} and {@link AxonServerConnectionManager} are not {@code null} and will
     * throw an {@link AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used.
     */
    protected AxonServerEventScheduler(Builder builder) {
        builder.validate();
        this.requestTimeout = builder.requestTimeout;
        this.serializer = builder.serializer.get();
        this.axonServerConnectionManager = builder.axonServerConnectionManager;
        this.context = builder.defaultContext;
        this.converter = new GrpcMetaDataConverter(serializer);
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onStart(Phase.OUTBOUND_EVENT_CONNECTORS, this::start);
        lifecycle.onShutdown(Phase.OUTBOUND_EVENT_CONNECTORS, this::shutdownDispatching);
    }

    /**
     * Start the Axon Server {@link EventScheduler} implementation.
     */
    public void start() {
        started.set(true);
    }

    /**
     * Shuts down the Axon Server {@link EventScheduler} implementation.
     */
    public void shutdownDispatching() {
        started.set(false);
    }

    /**
     * Schedules an event to be published at a given moment.  The event is published in the application's default
     * context.
     *
     * @param triggerDateTime The moment to trigger publication of the event
     * @param event           The event to publish
     * @return a token used to manage the schedule later
     */
    @Override
    public ScheduleToken schedule(Instant triggerDateTime, Object event) {
        Assert.isTrue(started.get(), () -> "Cannot dispatch new events as this scheduler is being shutdown");
        try {
            String response = eventChannel().scheduleEvent(triggerDateTime, toEvent(event))
                                            .get(requestTimeout, TimeUnit.MILLISECONDS);
            return new SimpleScheduleToken(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GrpcExceptionParser.parse(e);
        } catch (ExecutionException e) {
            throw GrpcExceptionParser.parse(e.getCause());
        } catch (TimeoutException e) {
            throw GrpcExceptionParser.parse(e);
        }
    }

    /**
     * Schedules an event to be published after a specified amount of time.
     *
     * @param triggerDuration the amount of time to wait before publishing the event
     * @param event           the event to publish
     * @return a token used to manage the schedule later
     */
    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        return schedule(Instant.now().plus(triggerDuration), event);
    }

    /**
     * Cancel a scheduled event.
     *
     * @param scheduleToken the token returned when the event was scheduled
     */
    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        Assert.isTrue(started.get(), () -> "Scheduler is being shutdown");
        Assert.isTrue(scheduleToken instanceof SimpleScheduleToken,
                      () -> "Invalid tracking token type. Must be SimpleScheduleToken.");
        String token = ((SimpleScheduleToken) scheduleToken).getTokenId();
        try {
            InstructionAck instructionAck = eventChannel().cancelSchedule(token)
                                                          .get(requestTimeout, TimeUnit.MILLISECONDS);
            if (!instructionAck.getSuccess()) {
                throw ErrorCode.getFromCode(instructionAck.getError().getErrorCode())
                               .convert(instructionAck.getError());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GrpcExceptionParser.parse(e);
        } catch (ExecutionException e) {
            throw GrpcExceptionParser.parse(e.getCause());
        } catch (TimeoutException e) {
            throw GrpcExceptionParser.parse(e);
        }
    }

    /**
     * Changes the trigger time for a scheduled event.
     *
     * @param scheduleToken   the token returned when the event was scheduled, might be null
     * @param triggerDuration the amount of time to wait before publishing the event
     * @param event           the event to publish
     * @return a token used to manage the schedule later
     */
    @Override
    public ScheduleToken reschedule(ScheduleToken scheduleToken, Duration triggerDuration, Object event) {
        Assert.isTrue(started.get(), () -> "Cannot dispatch new events as this scheduler is being shutdown");
        Assert.isTrue(scheduleToken == null || scheduleToken instanceof SimpleScheduleToken,
                      () -> "Invalid tracking token type. Must be SimpleScheduleToken.");
        String token = scheduleToken == null ? "" : ((SimpleScheduleToken) scheduleToken).getTokenId();
        try {
            String updatedScheduleToken = eventChannel().reschedule(token,
                                                                    Instant.now().plus(triggerDuration),
                                                                    toEvent(event))
                                                        .get(requestTimeout, TimeUnit.MILLISECONDS);
            return new SimpleScheduleToken(updatedScheduleToken);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GrpcExceptionParser.parse(e);
        } catch (ExecutionException e) {
            throw GrpcExceptionParser.parse(e.getCause());
        } catch (TimeoutException e) {
            throw GrpcExceptionParser.parse(e);
        }
    }

    private EventChannel eventChannel() {
        return StringUtils.nonEmptyOrNull(context) ? axonServerConnectionManager.getConnection(context).eventChannel()
                : axonServerConnectionManager.getConnection().eventChannel();
    }

    private Event toEvent(Object event) {
        SerializedObject<byte[]> serializedPayload;
        MetaData metadata;
        String requestId = null;
        if (event instanceof EventMessage<?>) {
            serializedPayload = ((EventMessage<?>) event)
                    .serializePayload(serializer, byte[].class);
            metadata = ((EventMessage<?>) event).getMetaData();
            requestId = ((EventMessage<?>) event).getIdentifier();
        } else {
            metadata = MetaData.emptyInstance();
            serializedPayload = serializer.serialize(event, byte[].class);
        }
        if (requestId == null) {
            requestId = IdentifierFactory.getInstance().generateIdentifier();
        }
        Event.Builder builder = Event.newBuilder()
                                     .setMessageIdentifier(requestId);
        builder.setPayload(
                io.axoniq.axonserver.grpc.SerializedObject.newBuilder()
                                                          .setType(serializedPayload.getType().getName())
                                                          .setRevision(getOrDefault(
                                                                  serializedPayload.getType().getRevision(),
                                                                  ""
                                                          ))
                                                          .setData(ByteString.copyFrom(serializedPayload.getData())));
        metadata.forEach((k, v) -> builder.putMetaData(k, converter.convertToMetaDataValue(v)));
        return builder.build();
    }

    /**
     * Builder class to instantiate an {@link AxonServerEventScheduler}.
     * <p>
     * The {@code requestTimeout} is defaulted to {@code 15000} millis.
     * <p>
     * The {@link Serializer} and {@link AxonServerConnectionManager} are <b>hard requirements</b> and as such should be
     * provided.
     */
    public static class Builder {

        private long requestTimeout = 15000;
        private Supplier<Serializer> serializer;
        private AxonServerConnectionManager axonServerConnectionManager;
        private String defaultContext;

        /**
         * Sets the timeout in which a confirmation of the scheduling interaction is expected. Defaults to 15 seconds.
         *
         * @param timeout the time to wait at most for a confirmation
         * @param unit    the unit in which the timeout is expressed
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder requestTimeout(long timeout, TimeUnit unit) {
            this.requestTimeout = unit.toMillis(timeout);
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize events.
         *
         * @param eventSerializer a {@link Serializer} used to de-/serialize events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventSerializer(Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The event Serializer may not be null");
            this.serializer = () -> eventSerializer;
            return this;
        }

        /**
         * Sets the {@link AxonServerConnectionManager} used to create connections between this application and an Axon
         * Server instance.
         *
         * @param axonServerConnectionManager an {@link AxonServerConnectionManager} used to create connections between
         *                                    this application and an Axon Server instance
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder connectionManager(AxonServerConnectionManager axonServerConnectionManager) {
            assertNonNull(axonServerConnectionManager, "AxonServerConnectionManager may not be null");
            this.axonServerConnectionManager = axonServerConnectionManager;
            return this;
        }

        /**
         * Sets the default context for this event scheduler to connect to.
         *
         * @param defaultContext for this scheduler to connect to.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder defaultContext(String defaultContext) {
            assertNonEmpty(defaultContext, "The context may not be null or empty");
            this.defaultContext = defaultContext;
            return this;
        }

        /**
         * Initializes a {@link AxonServerEventScheduler} as specified through this Builder.
         *
         * @return a {@link AxonServerEventScheduler} as specified through this Builder
         */
        public AxonServerEventScheduler build() {
            return new AxonServerEventScheduler(this);
        }

        protected void validate() throws AxonConfigurationException {
            if (serializer == null) {
                serializer = XStreamSerializer::defaultSerializer;
            }
            assertNonNull(axonServerConnectionManager,
                          "The AxonServerConnectionManager is a hard requirement and should be provided");
        }
    }
}

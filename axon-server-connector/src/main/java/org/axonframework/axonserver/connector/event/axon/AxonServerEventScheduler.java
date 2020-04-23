/*
 * Copyright (c) 2010-2020. Axon Framework
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
import io.axoniq.axonserver.grpc.InstructionAck;
import io.axoniq.axonserver.grpc.event.CancelScheduledEventRequest;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventSchedulerGrpc;
import io.axoniq.axonserver.grpc.event.RescheduleEventRequest;
import io.axoniq.axonserver.grpc.event.ScheduleEventRequest;
import io.grpc.Channel;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.event.util.GrpcExceptionParser;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Implementation of the {@link EventScheduler} that uses Axon Server to schedule the events.
 *
 * @author Marc Gathier
 * @since 4.4
 */
public class AxonServerEventScheduler implements EventScheduler {

    private final AxonServerConnectionManager axonServerConnectionManager;
    private final AxonServerConfiguration axonServerConfiguration;
    private final Serializer serializer;
    private final GrpcMetaDataConverter converter;
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * Instantiates an {@link AxonServerEventScheduler} using the given {@link Builder}.
     *
     * @param builder the {@link Builder} used.
     */
    protected AxonServerEventScheduler(Builder builder) {
        this.axonServerConnectionManager = builder.axonServerConnectionManager;
        this.axonServerConfiguration = builder.axonServerConfiguration;
        this.serializer = builder.eventSerializer.get();
        this.converter = new GrpcMetaDataConverter(serializer);
    }

    /**
     * Creates a builder to instantiate an {@link AxonServerEventScheduler}.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start the Axon Server {@link EventScheduler} implementation.
     */
    @StartHandler(phase = Phase.OUTBOUND_EVENT_CONNECTORS)
    public void start() {
        started.set(true);
    }

    @ShutdownHandler(phase = Phase.OUTBOUND_EVENT_CONNECTORS)
    public CompletableFuture<Void> shutdownDispatching() {
        started.set(false);
        return CompletableFuture.completedFuture(null);
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
        io.axoniq.axonserver.grpc.event.ScheduleToken response = null;
        try {
            response = eventSchedulerStub().scheduleEvent(ScheduleEventRequest
                                                                  .newBuilder()
                                                                  .setInstant(triggerDateTime.toEpochMilli())
                                                                  .setEvent(toEvent(event))
                                                                  .build())
                                           .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GrpcExceptionParser.parse(e);
        } catch (ExecutionException e) {
            throw GrpcExceptionParser.parse(e.getCause());
        }
        return new SimpleScheduleToken(response.getToken());
    }

    /**
     * Schedules an event to be published after a specified amount of time.
     *
     * @param triggerDuration The amount of time to wait before publishing the event
     * @param event           The event to publish
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
            InstructionAck instructionAck = eventSchedulerStub().cancelScheduledEvent(CancelScheduledEventRequest
                                                                                              .newBuilder()
                                                                                              .setToken(
                                                                                                      token)
                                                                                              .build())
                                                                .get();
            if (!instructionAck.getSuccess()) {
                throw ErrorCode.getFromCode(instructionAck.getError().getErrorCode()).convert(instructionAck
                                                                                                      .getError());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GrpcExceptionParser.parse(e);
        } catch (ExecutionException e) {
            throw GrpcExceptionParser.parse(e.getCause());
        }
    }

    private EventSchedulerGrpc.EventSchedulerFutureStub eventSchedulerStub() {
        Channel channel = axonServerConnectionManager.getChannel(axonServerConfiguration.getContext());
        return EventSchedulerGrpc.newFutureStub(channel);
    }

    /**
     * Changes the trigger time for a scheduled event.
     *
     * @param scheduleToken   the token returned when the event was scheduled, might be null
     * @param triggerDuration The amount of time to wait before publishing the event
     * @param event           The event to publish
     * @return a token used to manage the schedule later
     */
    @Override
    public ScheduleToken reschedule(ScheduleToken scheduleToken, Duration triggerDuration, Object event) {
        Assert.isTrue(started.get(), () -> "Cannot dispatch new events as this scheduler is being shutdown");
        Assert.isTrue(scheduleToken == null || scheduleToken instanceof SimpleScheduleToken,
                      () -> "Invalid tracking token type. Must be SimpleScheduleToken.");
        String token = scheduleToken == null ? "" : ((SimpleScheduleToken) scheduleToken).getTokenId();
        try {
            RescheduleEventRequest request =
                    RescheduleEventRequest.newBuilder()
                                          .setToken(token)
                                          .setEvent(toEvent(event))
                                          .setInstant(Instant.now()
                                                             .plus(triggerDuration)
                                                             .toEpochMilli())
                                          .build();
            io.axoniq.axonserver.grpc.event.ScheduleToken updatedScheduleToken = eventSchedulerStub()
                    .rescheduleEvent(request).get();
            return new SimpleScheduleToken(updatedScheduleToken.getToken());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw GrpcExceptionParser.parse(e);
        } catch (ExecutionException e) {
            throw GrpcExceptionParser.parse(e.getCause());
        }
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
     * Builder class for an {@link AxonServerEventScheduler}.
     * The {@link Serializer} defaults to the XStreamSerializer. The {@code configuration} and {@code connectionManager}
     * are required.
     */
    public static class Builder {

        private AxonServerConnectionManager axonServerConnectionManager;
        private AxonServerConfiguration axonServerConfiguration;
        private Supplier<Serializer> eventSerializer = XStreamSerializer::defaultSerializer;

        /**
         * Sets the {@link Serializer} used to de-/serialize events.
         *
         * @param eventSerializer a {@link Serializer} used to de-/serialize events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventSerializer(Serializer eventSerializer) {
            this.eventSerializer = () -> eventSerializer;
            return this;
        }

        /**
         * Sets the {@link AxonServerConfiguration} used to configure several components within the Axon Server Command
         * Bus, like setting the client id or the number of command handling threads used.
         *
         * @param configuration an {@link AxonServerConfiguration} used to configure several components within the Axon
         *                      Server Command Bus
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder configuration(AxonServerConfiguration configuration) {
            assertNonNull(configuration, "AxonServerConfiguration may not be null");
            this.axonServerConfiguration = configuration;
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
         * Initializes a {@link AxonServerEventScheduler} as specified through this Builder.
         *
         * @return a {@link AxonServerEventScheduler} as specified through this Builder
         */
        public AxonServerEventScheduler build() {
            validate();
            return new AxonServerEventScheduler(this);
        }

        protected void validate() throws AxonConfigurationException {
            assertNonNull(axonServerConfiguration,
                          "The AxonServerConfiguration is a hard requirement and should be provided");
            assertNonNull(axonServerConnectionManager,
                          "The AxonServerConnectionManager is a hard requirement and should be provided");
        }
    }
}

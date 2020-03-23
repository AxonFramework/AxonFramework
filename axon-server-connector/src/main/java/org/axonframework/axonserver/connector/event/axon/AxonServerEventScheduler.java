package org.axonframework.axonserver.connector.event.axon;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.InstructionAck;
import io.axoniq.axonserver.grpc.event.CancelScheduledeEventRequest;
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
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.ShutdownLatch;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * @author Marc Gathier
 */
public class AxonServerEventScheduler implements EventScheduler {

    private final AxonServerConnectionManager axonServerConnectionManager;
    private final AxonServerConfiguration axonServerConfiguration;
    private final Serializer serializer;
    private final GrpcMetaDataConverter converter;
    private final ShutdownLatch shutdownLatch = new ShutdownLatch();

    public AxonServerEventScheduler(Builder builder) {
        this.axonServerConnectionManager = builder.axonServerConnectionManager;
        this.axonServerConfiguration = builder.axonServerConfiguration;
        this.serializer = builder.eventSerializer.get();
        this.converter = new GrpcMetaDataConverter(serializer);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start the Axon Server {@link CommandBus} implementation.
     */
    @StartHandler(phase = Phase.OUTBOUND_EVENT_CONNECTORS)
    public void start() {
        shutdownLatch.initialize();
    }

    @ShutdownHandler(phase = Phase.OUTBOUND_EVENT_CONNECTORS)
    public CompletableFuture<Void> shutdownDispatching() {
        return shutdownLatch.initiateShutdown();
    }

    @Override
    public ScheduleToken schedule(Instant triggerDateTime, Object event) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new events as this scheduler is being shutdown");
        Channel channel = axonServerConnectionManager.getChannel(axonServerConfiguration.getContext());
        EventSchedulerGrpc.EventSchedulerFutureStub scheduleStub = EventSchedulerGrpc.newFutureStub(channel);
        io.axoniq.axonserver.grpc.event.ScheduleToken response = null;
        try {
            response = scheduleStub.scheduleEvent(ScheduleEventRequest
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

    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        return schedule(Instant.now().plus(triggerDuration), event);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new events as this scheduler is being shutdown");
        Assert.isTrue(scheduleToken instanceof SimpleScheduleToken,
                      () -> "Invalid tracking token type. Must be SimpleScheduleToken.");
        String token = ((SimpleScheduleToken) scheduleToken).getTokenId();
        Channel channel = axonServerConnectionManager.getChannel(axonServerConfiguration.getContext());
        EventSchedulerGrpc.EventSchedulerFutureStub scheduleStub = EventSchedulerGrpc.newFutureStub(channel);
        try {
            InstructionAck instructionAck = scheduleStub.cancelScheduledEvent(CancelScheduledeEventRequest.newBuilder()
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

    @Override
    public ScheduleToken reschedule(ScheduleToken scheduleToken, Duration triggerDuration, Object event) {
        shutdownLatch.ifShuttingDown("Cannot dispatch new events as this scheduler is being shutdown");
        Assert.isTrue(scheduleToken instanceof SimpleScheduleToken,
                      () -> "Invalid tracking token type. Must be SimpleScheduleToken.");
        String token = ((SimpleScheduleToken) scheduleToken).getTokenId();
        Channel channel = axonServerConnectionManager.getChannel(axonServerConfiguration.getContext());
        EventSchedulerGrpc.EventSchedulerFutureStub scheduleStub = EventSchedulerGrpc.newFutureStub(channel);
        try {
            InstructionAck instructionAck = scheduleStub.rescheduleEvent(RescheduleEventRequest.newBuilder()
                                                                                               .setToken(token)
                                                                                               .setInstant(Instant.now()
                                                                                                                  .plus(triggerDuration)
                                                                                                                  .toEpochMilli())
                                                                                               .build()).get();
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

        return scheduleToken;
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

    public static class Builder {

        private AxonServerConnectionManager axonServerConnectionManager;
        private AxonServerConfiguration axonServerConfiguration;
        private Supplier<Serializer> eventSerializer = XStreamSerializer::defaultSerializer;

        public Builder eventSerializer(Serializer eventSerializer) {
            this.eventSerializer = () -> eventSerializer;
            return this;
        }

        public Builder configuration(AxonServerConfiguration configuration) {
            assertNonNull(configuration, "AxonServerConfiguration may not be null");
            this.axonServerConfiguration = configuration;
            return this;
        }

        public Builder connectionManager(AxonServerConnectionManager axonServerConnectionManager) {
            assertNonNull(axonServerConnectionManager, "AxonServerConnectionManager may not be null");
            this.axonServerConnectionManager = axonServerConnectionManager;
            return this;
        }

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

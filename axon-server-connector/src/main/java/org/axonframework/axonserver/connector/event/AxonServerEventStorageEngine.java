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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.event.DcbEventChannel;
import io.axoniq.axonserver.grpc.event.dcb.GetHeadRequest;
import io.axoniq.axonserver.grpc.event.dcb.GetTailRequest;
import io.axoniq.axonserver.grpc.event.dcb.SourceEventsRequest;
import io.axoniq.axonserver.grpc.event.dcb.SourceEventsResponse;
import io.axoniq.axonserver.grpc.event.dcb.StreamEventsRequest;
import io.axoniq.axonserver.grpc.event.dcb.StreamEventsResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GlobalIndexConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.serialization.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * An {@link EventStorageEngine} implementation using Axon Server through the {@code axonserver-connector-java}
 * project.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AxonServerEventStorageEngine implements EventStorageEngine {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final AxonServerConnection connection;
    private final EventConverter converter;

    /**
     * Constructs an {@code AxonServerEventStorageEngine} with the given {@code connection} and {@code converter}.
     *
     * @param connection The context-specific backing connection to Axon Server.
     * @param converter  The converter to use to serialize {@link EventMessage#getPayload() payloads} and complex
     *                   {@link org.axonframework.messaging.MetaData} values into bytes.
     */
    public AxonServerEventStorageEngine(@Nonnull AxonServerConnection connection,
                                        @Nonnull Converter converter) {
        this.connection = Objects.requireNonNull(connection, "The Axon Server connection cannot be null.");
        this.converter = new EventConverter(converter);
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {
        DcbEventChannel.AppendEventsTransaction appendEventsTransaction = eventChannel().startTransaction();
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        } else {
            events.stream()
                  .map(converter::convertTaggedEventMessage)
                  .forEach(taggedEvent -> {
                      if (logger.isDebugEnabled()) {
                          logger.debug("Appended event [{}] with timestamp [{}].",
                                       taggedEvent.getEvent().getIdentifier(),
                                       taggedEvent.getEvent().getTimestamp());
                      }
                      appendEventsTransaction.append(taggedEvent);
                  });
        }

        return CompletableFuture.completedFuture(new AppendTransaction() {
            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                return appendEventsTransaction.commit().thenApply(
                        appendResponse -> new GlobalIndexConsistencyMarker(appendResponse.getLastPosition())
                );
            }

            @Override
            public void rollback() {
                appendEventsTransaction.rollback();
            }
        });
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        if (logger.isDebugEnabled()) {
            logger.debug("Start sourcing events with condition [{}].", condition);
        }

        // TODO Do we disregard SourcingCondition#end for now?
        //  Axon Server UI does not support this, so we would have to cut off ourselves in the result stream.
        SourceEventsRequest sourcingRequest = ConditionConverter.convertSourcingCondition(condition);
        ResultStream<SourceEventsResponse> sourcingStream = eventChannel().source(sourcingRequest);
        return new SourcingMessageStream(sourcingStream, converter);
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        if (logger.isDebugEnabled()) {
            logger.debug("Start streaming events with condition [{}].", condition);
        }

        StreamEventsRequest streamingRequest = ConditionConverter.convertStreamingCondition(condition);
        ResultStream<StreamEventsResponse> stream = eventChannel().stream(streamingRequest);
        return new StreamingMessageStream(stream, converter);
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation tailToken() is invoked.");
        }

        return eventChannel().tail(GetTailRequest.newBuilder().build())
                             .thenApply(response -> new GlobalSequenceTrackingToken(response.getSequence()));
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation headToken() is invoked.");
        }

        return eventChannel().head(GetHeadRequest.newBuilder().build())
                             .thenApply(response -> new GlobalSequenceTrackingToken(response.getSequence()));
    }

    private DcbEventChannel eventChannel() {
        return connection.dcbEventChannel();
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation tokenAt() is invoked with Instant [{}].", at);
        }

        throw new UnsupportedOperationException("The Axon Server Connector does not yet support this operation.");
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("connection", connection);
        descriptor.describeProperty("converter", converter);
    }
}

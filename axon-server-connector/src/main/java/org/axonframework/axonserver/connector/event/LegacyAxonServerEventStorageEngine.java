/*
 * Copyright (c) 2010-2024. Axon Framework
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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.event.AggregateEventStream;
import io.axoniq.axonserver.connector.event.AppendEventsTransaction;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Context;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AppendConditionAssertionException;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.Index;
import org.axonframework.eventsourcing.eventstore.IndexedEventMessage;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Converter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class LegacyAxonServerEventStorageEngine implements AsyncEventStorageEngine {

    private final AxonServerConnection connection;
    private final Converter payloadConverter;

    public LegacyAxonServerEventStorageEngine(io.axoniq.axonserver.connector.AxonServerConnection connection,
                                              Converter payloadConverter) {
        this.connection = connection;
        this.payloadConverter = payloadConverter;
    }

    @Nullable
    private static String resolveAggregateIdentifier(Set<Index> indices) {
        if (indices.isEmpty()) {
            return null;
        } else if (indices.size() > 1) {
            throw new IllegalArgumentException("condition must provide exactly one index");
        } else {
            return indices.iterator().next().value();
        }
    }

    private static String resolveAggregateType(Set<Index> indices) {
        if (indices.isEmpty()) {
            return null;
        } else if (indices.size() > 1) {
            throw new IllegalArgumentException("condition must provide as most one index");
        } else {
            return indices.iterator().next().key();
        }
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<IndexedEventMessage<?>> events) {
        if (condition.criteria().indices().size() > 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Condition must provide at most one index"));
        }
        String aggregateType = resolveAggregateType(condition.criteria().indices());
        String aggregateIdentifier = resolveAggregateIdentifier(condition.criteria().indices());
        AtomicLong consistencyMarker = new AtomicLong(condition.consistencyMarker());

        if (aggregateIdentifier != null && condition.consistencyMarker() == Long.MAX_VALUE) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "ConsistencyMarker must be provided explicitly in legacy mode"));
        }

        AppendEventsTransaction tx = connection.eventChannel().startAppendEventsTransaction();
        events.forEach(event -> {
            byte[] payload = payloadConverter.convert(event.getPayload(), byte[].class);
            Event.Builder builder = Event.newBuilder().setPayload(SerializedObject.newBuilder()
                                                                                  .setData(ByteString.copyFrom(payload))
                                                                                  // TODO - Make the type explicit on Message
                                                                                  .setType(event.getPayload().getClass()
                                                                                                .getName())
                                                                                  // TODO - Make revision explicit on Message
                                                                                  .setRevision("").build())
                                         .setMessageIdentifier(event.getIdentifier()).setTimestamp(event.getTimestamp()
                                                                                                        .toEpochMilli());
            if (aggregateIdentifier != null && aggregateType != null) {
                long aggregateSequenceNumber = consistencyMarker.incrementAndGet();
                builder.setAggregateIdentifier(aggregateIdentifier).setAggregateType(aggregateType)
                       .setAggregateSequenceNumber(aggregateSequenceNumber);
            }
            buildMetaData(event.getMetaData(), builder.getMetaDataMap());
            Event message = builder.build();
            tx.appendEvent(message);
        });

        return CompletableFuture.completedFuture(new AppendTransaction() {
            @Override
            public CompletableFuture<Long> commit() {
                return tx.commit()
                         .exceptionallyCompose(e -> CompletableFuture.failedFuture(translateConflictException(e)))
                         .thenApply(r -> consistencyMarker.get());
            }

            private Throwable translateConflictException(Throwable e) {
                if (e instanceof StatusRuntimeException sre && Objects.equals(sre.getStatus().getCode(),
                                                                              Status.OUT_OF_RANGE.getCode())) {
                    AppendConditionAssertionException translated = AppendConditionAssertionException.consistencyMarkerSurpassed(
                            consistencyMarker.get());
                    translated.addSuppressed(e);
                    return translated;
                }
                if (e.getCause() != null) {
                    Throwable translatedCause = translateConflictException(e.getCause());
                    if (translatedCause != e.getCause()) {
                        return translatedCause;
                    }
                }
                return e;
            }

            @Override
            public void rollback() {
                tx.rollback();
            }
        });
    }

    private void buildMetaData(MetaData metaData, Map<String, MetaDataValue> metaDataMap) {
        metaData.forEach((k, v) -> {
            MetaDataValue result = null;
            if (v instanceof CharSequence c) {
                result = MetaDataValue.newBuilder().setTextValue(c.toString()).build();
            } else if (v instanceof Number n) {
                if (n instanceof Float || n instanceof Double) {
                    result = MetaDataValue.newBuilder().setDoubleValue(n.doubleValue()).build();
                } else {
                    result = MetaDataValue.newBuilder().setNumberValue(n.longValue()).build();
                }
            } else if (v instanceof Boolean b) {
                result = MetaDataValue.newBuilder().setBooleanValue(b).build();
            }
            metaDataMap.put(k, result);
        });
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        String aggregateIdentifier = resolveAggregateIdentifier(condition.criteria().indices());
        AggregateEventStream aggregateStream = connection.eventChannel().openAggregateStream(aggregateIdentifier);
        return MessageStream.fromStream(aggregateStream.asStream(), this::convertToMessage, event -> Context.with(
                                                                                                                    LegacyResources.AGGREGATE_IDENTIFIER_KEY,
                                                                                                                    event.getAggregateIdentifier()).withResource(LegacyResources.AGGREGATE_TYPE_KEY,
                                                                                                                                                                 event.getAggregateType())
                                                                                                            .withResource(
                                                                                                                    LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY,
                                                                                                                    event.getAggregateSequenceNumber()));
    }

    private EventMessage<byte[]> convertToMessage(Event event) {
        return new GenericEventMessage<>(event.getMessageIdentifier(),
                                         event.getPayload().getData().toByteArray(),
                                         getMetaData(event.getMetaDataMap()),
                                         Instant.ofEpochMilli(event.getTimestamp()));
    }

    private MetaData getMetaData(Map<String, MetaDataValue> metaDataMap) {
        MetaData metaData = MetaData.emptyInstance();
        for (Map.Entry<String, MetaDataValue> entry : metaDataMap.entrySet()) {
            Object value = convertFromMetaDataValue(entry.getValue());
            if (value != null) {
                metaData = metaData.and(entry.getKey(), value);
            }
        }
        return metaData;
    }

    public Object convertFromMetaDataValue(MetaDataValue value) {
        switch (value.getDataCase()) {
            case TEXT_VALUE:
                return value.getTextValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case NUMBER_VALUE:
                return value.getNumberValue();
            case BOOLEAN_VALUE:
                return value.getBooleanValue();
            case DATA_NOT_SET, BYTES_VALUE:
            default:
                return null;
        }
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        TrackingToken trackingToken = condition.position();
        if (trackingToken instanceof GlobalSequenceTrackingToken gtt) {
            return new AxonServerMessageStream(connection.eventChannel().openStream(gtt.getGlobalIndex(), 32),
                                               this::convertToMessage);
        } else {
            throw new IllegalArgumentException(
                    "Tracking Token is not of expected type. Must be GlobalTrackingToken. Is: "
                            + trackingToken.getClass().getName());
        }
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        return connection.eventChannel().getFirstToken().thenApply(GlobalSequenceTrackingToken::new);
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        return connection.eventChannel().getLastToken().thenApply(GlobalSequenceTrackingToken::new);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return connection.eventChannel().getTokenAt(at.toEpochMilli()).thenApply(GlobalSequenceTrackingToken::new);
    }

    @Override
    public void describeTo(@javax.annotation.Nonnull ComponentDescriptor descriptor) {

    }
}

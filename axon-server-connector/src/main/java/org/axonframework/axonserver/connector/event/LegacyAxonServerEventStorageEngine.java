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
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.LegacyResources;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.command.ConflictingModificationException;
import org.axonframework.serialization.Converter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Event Storage Engine implementation that uses the aggregate-oriented APIs of Axon Server, allowing it to interact
 * with versions that do not have DCB support.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class LegacyAxonServerEventStorageEngine implements AsyncEventStorageEngine {

    private final AxonServerConnection connection;
    private final Converter payloadConverter;

    /**
     * Initialize the {@code LegacyAxonServerEventStorageEngine} with given {@code connection} to Axon Server and given
     * {@code payloadConverter} to convert payloads of appended messages (to bytes).
     *
     * @param connection       The backing connection to Axon Server
     * @param payloadConverter The converter to use to serialize payloads to bytes
     */
    public LegacyAxonServerEventStorageEngine(@Nonnull AxonServerConnection connection,
                                              @Nonnull Converter payloadConverter) {
        this.connection = connection;
        this.payloadConverter = payloadConverter;
    }

    @Nullable
    private static String resolveAggregateIdentifier(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().value();
        }
    }

    @Nullable
    private static String resolveAggregateType(Set<Tag> indices) {
        if (indices.isEmpty()) {
            return null;
        } else if (indices.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return indices.iterator().next().key();
        }
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {

        AggregateBasedConsistencyMarker consistencyMarker = AggregateBasedConsistencyMarker.from(condition);
        Map<String, AtomicLong> aggregateSequences = new HashMap<>();

        try {
            assertValidTags(events);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        AppendEventsTransaction tx = connection.eventChannel().startAppendEventsTransaction();
        try {
            events.forEach(taggedEvent -> {
                EventMessage<?> event = taggedEvent.event();
                byte[] payload = payloadConverter.convert(event.getPayload(), byte[].class);
                Event.Builder builder = Event.newBuilder()
                                             .setPayload(SerializedObject.newBuilder()
                                                                         .setData(ByteString.copyFrom(payload))
                                                                         .setType(event.name().namespace() + "."
                                                                                          + event.name().localName())
                                                                         .setRevision(event.name().revision())
                                                                         .build())
                                             .setMessageIdentifier(event.getIdentifier())
                                             .setTimestamp(event.getTimestamp().toEpochMilli());
                String aggregateIdentifier = resolveAggregateIdentifier(taggedEvent.tags());
                String aggregateType = resolveAggregateType(taggedEvent.tags());
                if (aggregateIdentifier != null && aggregateType != null && !taggedEvent.tags().isEmpty()) {
                    long nextSequence = resolveSequencer(aggregateSequences, aggregateIdentifier, consistencyMarker).incrementAndGet();
                    builder.setAggregateIdentifier(aggregateIdentifier).setAggregateType(aggregateType)
                           .setAggregateSequenceNumber(nextSequence);
                }
                buildMetaData(event.getMetaData(), builder.getMetaDataMap());
                Event message = builder.build();
                tx.appendEvent(message);
            });
        } catch (Exception e) {
            tx.rollback();
            return CompletableFuture.failedFuture(e);
        }

        return CompletableFuture.completedFuture(new AppendTransaction() {
            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                AggregateBasedConsistencyMarker newConsistencyMarker = consistencyMarker;
                for (Map.Entry<String, AtomicLong> aggSeq : aggregateSequences.entrySet()) {
                    newConsistencyMarker = newConsistencyMarker.forwarded(aggSeq.getKey(), aggSeq.getValue().get());
                }
                var finalConsistencyMarker = newConsistencyMarker;
                return tx.commit()
                         .exceptionallyCompose(e -> CompletableFuture.failedFuture(translateConflictException(e)))
                         .thenApply(r -> finalConsistencyMarker);
            }

            private Throwable translateConflictException(Throwable e) {
                if (e instanceof StatusRuntimeException sre && Objects.equals(sre.getStatus().getCode(),
                                                                              Status.OUT_OF_RANGE.getCode())) {
                    ConflictingModificationException translated = new ConflictingModificationException(
                            "Conflicting changes detected beyond provided consistency marker");
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

    private static AtomicLong resolveSequencer(Map<String, AtomicLong> aggregateSequences, String aggregateIdentifier,
                                               AggregateBasedConsistencyMarker consistencyMarker) {
        return aggregateSequences.computeIfAbsent(aggregateIdentifier,
                                                  i -> new AtomicLong(consistencyMarker.positionOf(i)));
    }

    private void assertValidTags(List<TaggedEventMessage<?>> events) {
        for (TaggedEventMessage<?> taggedEvent : events) {
            if (taggedEvent.tags().size() > 1) {
                throw new IllegalArgumentException(
                        "An Event Storage engine in Aggregate mode does not support multiple tags per event");
            }
        }
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
        MessageStream<EventMessage<?>> resultingStream = MessageStream.empty();
        for (EventCriteria criterion : condition.criteria()) {
            String aggregateIdentifier = resolveAggregateIdentifier(criterion.tags());
            // axonserver uses 0 to denote the end of a stream, so if 0 is provided, we use 1. For infinity, we use 0.
            long end = condition.end() == Long.MAX_VALUE ? 0 : condition.end() + 1;
            AggregateEventStream aggregateStream = connection.eventChannel().openAggregateStream(aggregateIdentifier, condition.start(), end);
            resultingStream = resultingStream.concatWith(MessageStream.fromStream(
                    aggregateStream.asStream(),
                    this::convertToMessage,
                    event -> Context.with(LegacyResources.AGGREGATE_IDENTIFIER_KEY, event.getAggregateIdentifier())
                                    .withResource(LegacyResources.AGGREGATE_TYPE_KEY, event.getAggregateType())
                                    .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY,
                                                  event.getAggregateSequenceNumber())
                                    .withResource(ConsistencyMarker.RESOURCE_KEY,
                                                  new AggregateBasedConsistencyMarker(event.getAggregateIdentifier(),
                                                                                      event.getAggregateSequenceNumber()))));
        }
        AtomicReference<ConsistencyMarker> consistencyMarker = new AtomicReference<>();
        return resultingStream.map(e -> {
            ConsistencyMarker newMarker = consistencyMarker.accumulateAndGet(e.getResource(ConsistencyMarker.RESOURCE_KEY),
                                                                             (m1, m2) -> m1
                                                                                     == null ? m2 : m1.upperBound(m2));
            return e.withResource(ConsistencyMarker.RESOURCE_KEY, newMarker);
        });
    }

    private EventMessage<byte[]> convertToMessage(Event event) {
        return new GenericEventMessage<>(event.getMessageIdentifier(),
                                         new QualifiedName("test", "event", "0.0.1"),
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

    private Object convertFromMetaDataValue(MetaDataValue value) {
        return switch (value.getDataCase()) {
            case TEXT_VALUE -> value.getTextValue();
            case DOUBLE_VALUE -> value.getDoubleValue();
            case NUMBER_VALUE -> value.getNumberValue();
            case BOOLEAN_VALUE -> value.getBooleanValue();
            default -> null;
        };
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
        descriptor.describeProperty("connection", connection);
        descriptor.describeProperty("payloadConverter", payloadConverter);
    }
}

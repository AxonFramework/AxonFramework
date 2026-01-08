/*
 * Copyright (c) 2010-2026. Axon Framework
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
import org.axonframework.axonserver.connector.MetadataConverter;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker.AggregateSequencer;
import org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils;
import org.axonframework.eventsourcing.eventstore.AggregateSequenceNumberPosition;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EmptyAppendTransaction;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.TerminalEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.EventCriterion;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventstreaming.Tag;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.axonframework.eventsourcing.eventstore.AggregateBasedEventStorageEngineUtils.*;

/**
 * Event Storage Engine implementation that uses the aggregate-oriented APIs of Axon Server, allowing it to interact
 * with versions that do not have DCB support.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class AggregateBasedAxonServerEventStorageEngine implements EventStorageEngine {

    private final AxonServerConnection connection;
    private final EventConverter converter;

    /**
     * Initialize the {@code LegacyAxonServerEventStorageEngine} with given {@code connection} to Axon Server and given
     * {@code payloadConverter} to convert payloads of appended messages (to bytes).
     *
     * @param connection       The backing connection to Axon Server
     * @param converter The converter to use to serialize payloads to bytes
     */
    public AggregateBasedAxonServerEventStorageEngine(@Nonnull AxonServerConnection connection,
                                                      @Nonnull EventConverter converter) {
        this.connection = Objects.requireNonNull(connection, "The connection must not be null.");
        this.converter = Objects.requireNonNull(converter, "The converter must not be null.");
    }

    @Override
    public CompletableFuture<AppendTransaction<?>> appendEvents(@Nonnull AppendCondition condition,
                                                                @Nullable ProcessingContext context,
                                                                @Nonnull List<TaggedEventMessage<?>> events) {
        try {
            assertValidTags(events);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(EmptyAppendTransaction.INSTANCE);
        }

        AggregateBasedConsistencyMarker consistencyMarker = AggregateBasedConsistencyMarker.from(condition);
        AggregateSequencer aggregateSequencer = consistencyMarker.createSequencer();

        AppendEventsTransaction tx = connection.eventChannel().startAppendEventsTransaction();
        try {
            events.forEach(taggedEvent -> {
                EventMessage event = taggedEvent.event();
                ByteString payloadData = ByteString.copyFrom(event.payloadAs(byte[].class, converter));
                Event.Builder builder = Event.newBuilder()
                                             .setPayload(SerializedObject.newBuilder()
                                                                         .setData(payloadData)
                                                                         .setType(event.type().name())
                                                                         .setRevision(event.type().version())
                                                                         .build())
                                             .setMessageIdentifier(event.identifier())
                                             .setTimestamp(event.timestamp().toEpochMilli());
                String aggregateIdentifier = resolveAggregateIdentifier(taggedEvent.tags());
                String aggregateType = resolveAggregateType(taggedEvent.tags());
                if (aggregateIdentifier != null && aggregateType != null && !taggedEvent.tags().isEmpty()) {
                    long nextSequence = aggregateSequencer.incrementAndGetSequenceOf(aggregateIdentifier);
                    builder.setAggregateIdentifier(aggregateIdentifier).setAggregateType(aggregateType)
                           .setAggregateSequenceNumber(nextSequence);
                }
                var modifiableMetadataMap = new HashMap<>(builder.getMetaDataMap());
                buildMetadata(event.metadata(), modifiableMetadataMap);
                Event message = builder.build();
                tx.appendEvent(message);
            });
        } catch (Exception e) {
            tx.rollback();
            return CompletableFuture.failedFuture(e);
        }

        return CompletableFuture.completedFuture(new AppendTransaction<AggregateBasedConsistencyMarker>() {
            @Override
            public CompletableFuture<AggregateBasedConsistencyMarker> commit(@Nullable ProcessingContext context) {
                return tx.commit()
                         .exceptionallyCompose(e -> CompletableFuture.failedFuture(translateConflictException(e)))
                         .thenApply(r -> aggregateSequencer.toMarker());
            }

            @Override
            public CompletableFuture<ConsistencyMarker> afterCommit(@Nonnull AggregateBasedConsistencyMarker marker, @Nullable ProcessingContext context) {
                return CompletableFuture.completedFuture(marker);
            }

            private Throwable translateConflictException(Throwable e) {
                Predicate<Throwable> isConflictException = (ex) -> ex instanceof StatusRuntimeException sre
                        && Objects.equals(sre.getStatus().getCode(), Status.OUT_OF_RANGE.getCode());
                return AggregateBasedEventStorageEngineUtils
                        .translateConflictException(consistencyMarker, e, isConflictException);
            }

            @Override
            public void rollback(@Nullable ProcessingContext context) {
                tx.rollback();
            }
        });
    }

    private void buildMetadata(Metadata metadata, Map<String, MetaDataValue> metadataMap) {
        metadata.forEach((k, v) -> metadataMap.put(k, MetaDataValue.newBuilder().setTextValue(v).build()));
    }

    @Override
    public MessageStream<EventMessage> source(@Nonnull SourcingCondition condition, @Nullable ProcessingContext context) {
        CompletableFuture<Void> endOfStreams = new CompletableFuture<>();
        List<AggregateSource> aggregateSources = condition.criteria()
                                                          .flatten()
                                                          .stream()
                                                          .map(criterion -> this.aggregateSourceForCriterion(
                                                                  condition, criterion
                                                          ))
                                                          .toList();

        return aggregateSources.stream()
                               .map(AggregateSource::source)
                               .reduce(MessageStream.empty().cast(), MessageStream::concatWith)
                               .onComplete(() -> endOfStreams.complete(null))
                               .concatWith(MessageStream.fromFuture(
                                       endOfStreams.thenApply(event -> TerminalEventMessage.INSTANCE),
                                       unused -> Context.with(
                                               ConsistencyMarker.RESOURCE_KEY,
                                               combineAggregateMarkers(aggregateSources.stream())
                                       )
                               ));
    }

    private AggregateSource aggregateSourceForCriterion(SourcingCondition condition, EventCriterion criterion) {
        AtomicReference<AggregateBasedConsistencyMarker> markerReference = new AtomicReference<>();
        String aggregateIdentifier = resolveAggregateIdentifier(criterion.tags());
        AggregateEventStream aggregateStream =
                connection.eventChannel()
                          .openAggregateStream(aggregateIdentifier, AggregateSequenceNumberPosition.toSequenceNumber(condition.start()));

        MessageStream<EventMessage> source =
                MessageStream.fromStream(aggregateStream.asStream(),
                                         this::convertToMessage,
                                         event -> setMarkerAndBuildContext(event.getAggregateIdentifier(),
                                                                           event.getAggregateSequenceNumber(),
                                                                           event.getAggregateType(),
                                                                           markerReference))
                             // Defaults the marker when the aggregate stream was empty
                             .onComplete(() -> markerReference.compareAndSet(
                                     null, new AggregateBasedConsistencyMarker(aggregateIdentifier, 0)
                             ))
                             .cast();
        return new AggregateSource(markerReference, source);
    }

    private static Context setMarkerAndBuildContext(String aggregateIdentifier,
                                                    long sequenceNumber,
                                                    String aggregateType,
                                                    AtomicReference<AggregateBasedConsistencyMarker> markerReference) {
        markerReference.set(new AggregateBasedConsistencyMarker(aggregateIdentifier, sequenceNumber));
        return Context.with(LegacyResources.AGGREGATE_IDENTIFIER_KEY, aggregateIdentifier)
                      .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, sequenceNumber)
                      .withResource(LegacyResources.AGGREGATE_TYPE_KEY, aggregateType);
    }

    private static ConsistencyMarker combineAggregateMarkers(Stream<AggregateSource> resultStream) {
        return resultStream.map(AggregateSource::markerReference)
                           .map(AtomicReference::get)
                           .map(marker -> (ConsistencyMarker) marker)
                           .reduce(ConsistencyMarker::upperBound)
                           .orElseThrow();
    }

    @Override
    public MessageStream<EventMessage> stream(@Nonnull StreamingCondition condition, @Nullable ProcessingContext context) {
        TrackingToken trackingToken = condition.position();
        if (trackingToken instanceof GlobalSequenceTrackingToken gtt) {
            return new AxonServerMessageStream(connection.eventChannel().openStream(gtt.getGlobalIndex(), 32),
                                               this::convertToMessage)
                .filter(e -> {
                    String type = e.getResource(LegacyResources.AGGREGATE_TYPE_KEY);
                    String identifier = e.getResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY);
                    Set<Tag> tags = type == null || identifier == null ? Set.of() : Set.of(new Tag(type, identifier));

                    return condition.matches(e.message().type().qualifiedName(), tags);
                });
        } else {
            throw new IllegalArgumentException(
                    "Tracking Token is not of expected type. Must be GlobalTrackingToken. Is: "
                            + trackingToken.getClass().getName());
        }
    }

    private EventMessage convertToMessage(Event event) {
        SerializedObject payload = event.getPayload();
        return new GenericEventMessage(
                event.getMessageIdentifier(),
                new MessageType(payload.getType(), payload.getRevision()),
                payload.getData().toByteArray(),
                getMetadata(event.getMetaDataMap()),
                Instant.ofEpochMilli(event.getTimestamp())
        );
    }

    private Metadata getMetadata(Map<String, MetaDataValue> metadataMap) {
        return new Metadata(MetadataConverter.convertMetadataValuesToGrpc(metadataMap));
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken(@Nullable ProcessingContext context) {
        return connection.eventChannel()
                         .getFirstToken()
                         .thenApply(GlobalSequenceTrackingToken::new);
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken(@Nullable ProcessingContext context) {
        return connection.eventChannel()
                         .getLastToken()
                         .thenApply(GlobalSequenceTrackingToken::new);
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at, @Nullable ProcessingContext context) {
        return connection.eventChannel().getTokenAt(at.toEpochMilli()).thenApply(GlobalSequenceTrackingToken::new);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("connection", connection);
        descriptor.describeProperty("converter", converter);
    }

    /**
     * A tuple of an {@link AtomicReference} to an {@link AggregateBasedConsistencyMarker} and a {@link MessageStream},
     * used when sourcing events from an aggregate-specific {@link EventCriterion}. This tuple object can then be used
     * to {@link MessageStream#concatWith(MessageStream) construct a single stream}, completing with a final marker.
     */
    private record AggregateSource(
            AtomicReference<AggregateBasedConsistencyMarker> markerReference,
            MessageStream<EventMessage> source
    ) {

    }
}
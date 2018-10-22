/*
 * Copyright (c) 2010-2018. Axon Framework
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
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.event.GetAggregateEventsRequest;
import io.axoniq.axonserver.grpc.event.GetAggregateSnapshotsRequest;
import io.axoniq.axonserver.grpc.event.GetEventsRequest;
import io.axoniq.axonserver.grpc.event.QueryEventsRequest;
import io.axoniq.axonserver.grpc.event.QueryEventsResponse;
import io.axoniq.axonserver.grpc.event.ReadHighestSequenceNrResponse;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.event.AppendEventTransaction;
import org.axonframework.axonserver.connector.event.AxonServerEventStoreClient;
import org.axonframework.axonserver.connector.util.FlowControllingStreamObserver;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.EventStreamUtils;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.AbstractEventStore;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.*;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Axon EventStore implementation that connects to the AxonIQ AxonServer Server to store and retrieve Events.
 *
 * @author Zoltan Altfatter
 * @author Marc Gathier
 * @author Allard Buijze
 */
public class AxonServerEventStore extends AbstractEventStore {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerEventStore.class);

    /**
     * Instantiate a {@link AxonServerEventStore} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link EventStorageEngine} is set. If not, the {@link AxonServerConfiguration} and
     * {@link AxonServerConnectionManager} should minimally be provided to create an AxonServer specific
     * EventStorageEngine implementation. If either of these {@code null} assertions fail, an
     * {@link AxonConfigurationException} will be thrown.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AxonServerEventStore} instance
     */
    protected AxonServerEventStore(Builder builder) {
        super(builder);
    }

    /**
     * Instantiate a Builder to be able to create a {@link AxonServerEventStore}.
     * <p>
     * The main goal of this Builder is to instantiate an AxonServer specific {@link EventStorageEngine}. The properties
     * which may be provided through this Builder are thus all used to end up with that EventStorageEngine
     * implementation. An EventStorageEngine may be provided directly however, although we encourage the usage of the
     * {@link Builder#configuration} and {@link Builder#axonServerConnectionManager} functions to let it be created.
     * <p>
     * The snapshot {@link Serializer} is defaulted to a {@link XStreamSerializer}, the event Serializer also defaults
     * to a XStreamSerializer and the {@link EventUpcaster} defaults to a {@link NoOpEventUpcaster}.
     * The {@link AxonServerConfiguration} and {@link AxonServerConnectionManager} are <b>hard requirements</b> if no
     * EventStorageEngine is provided directly.
     *
     * @return a Builder to be able to create a {@link AxonServerEventStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public TrackingEventStream openStream(TrackingToken trackingToken) {
        return storageEngine().openStream(trackingToken);
    }

    public QueryResultStream query(String query, boolean liveUpdates) {
        return storageEngine().query(query, liveUpdates);
    }

    @Override
    protected AxonIQEventStorageEngine storageEngine() {
        return (AxonIQEventStorageEngine) super.storageEngine();
    }

    /**
     * Builder class to instantiate a {@link AxonServerEventStore}.
     * <p>
     * The main goal of this Builder is to instantiate an AxonServer specific {@link EventStorageEngine}. The properties
     * which may be provided through this Builder are thus all used to end up with that EventStorageEngine
     * implementation. An EventStorageEngine may be provided directly however, although we encourage the usage of the
     * {@link Builder#configuration} and {@link Builder#axonServerConnectionManager} functions to let it be created.
     * <p>
     * The snapshot {@link Serializer} is defaulted to a {@link XStreamSerializer}, the event Serializer also defaults
     * to a XStreamSerializer and the {@link EventUpcaster} defaults to a {@link NoOpEventUpcaster}.
     * The {@link AxonServerConfiguration} and {@link AxonServerConnectionManager} are <b>hard requirements</b> if no
     * EventStorageEngine is provided directly.
     */
    public static class Builder extends AbstractEventStore.Builder {

        private AxonServerConfiguration configuration;
        private AxonServerConnectionManager axonServerConnectionManager;
        private Serializer snapshotSerializer = XStreamSerializer.builder().build();
        private Serializer eventSerializer = XStreamSerializer.builder().build();
        private EventUpcaster upcasterChain = NoOpEventUpcaster.INSTANCE;
        private Predicate<? super DomainEventData<?>> snapshotFilter;

        @Override
        public Builder storageEngine(EventStorageEngine storageEngine) {
            super.storageEngine(storageEngine);
            return this;
        }

        @Override
        public Builder messageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        /**
         * Sets the {@link AxonServerConfiguration} describing the servers to connect with and how to manage flow
         * control.
         * <p>
         * This object is used by the AxonServer {@link EventStorageEngine} implementation which this Builder will
         * create if it is not provided. We suggest to use these, instead of the {@link Builder#storageEngine()}
         * function to set the EventStorageEngine.
         *
         * @param configuration the {@link AxonServerConfiguration} describing the servers to connect with and how to
         *                      manage flow control
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder configuration(AxonServerConfiguration configuration) {
            assertNonNull(configuration, "AxonServerConfiguration may not be null");
            this.configuration = configuration;
            return this;
        }

        /**
         * Sets the {@link AxonServerConnectionManager} managing the connections to the AxonServer platform.
         * <p>
         * This object is used by the AxonServer {@link EventStorageEngine} implementation which this Builder will
         * create if it is not provided. We suggest to use these, instead of the {@link Builder#storageEngine()}
         * function to set the EventStorageEngine.
         *
         * @param axonServerConnectionManager the {@link AxonServerConnectionManager} managing the connections to the
         *                                    AxonServer platform
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder platformConnectionManager(AxonServerConnectionManager axonServerConnectionManager) {
            assertNonNull(axonServerConnectionManager, "PlatformConnectionManager may not be null");
            this.axonServerConnectionManager = axonServerConnectionManager;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to serialize and deserialize snapshots. Defaults to a
         * {@link XStreamSerializer}.
         * <p>
         * This object is used by the AxonServer {@link EventStorageEngine} implementation which this Builder will
         * create if it is not provided. We suggest to use these, instead of the {@link Builder#storageEngine()}
         * function to set the EventStorageEngine.
         *
         * @param snapshotSerializer a {@link Serializer} used to serialize and deserialize snapshots
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder snapshotSerializer(Serializer snapshotSerializer) {
            assertNonNull(snapshotSerializer, "The Snapshot Serializer may not be null");
            this.snapshotSerializer = snapshotSerializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to serialize and deserialize the Event Message's payload and Meta Data
         * with. Defaults to a {@link XStreamSerializer}.
         * <p>
         * This object is used by the AxonServer {@link EventStorageEngine} implementation which this Builder will
         * create if it is not provided. We suggest to use these, instead of the {@link Builder#storageEngine()}
         * function to set the EventStorageEngine.
         *
         * @param eventSerializer The serializer to serialize the Event Message's payload and Meta Data with
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventSerializer(Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The Event Serializer may not be null");
            this.eventSerializer = eventSerializer;
            return this;
        }

        /**
         * Sets the {@link Predicate} used to filter snapshots when returning aggregate events. When not set all
         * snapshots are used.
         * <p>
         * This object is used by the AxonServer {@link EventStorageEngine} implementation.
         *
         * @param snapshotFilter The snapshot filter predicate
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
            assertNonNull(snapshotFilter, "The Snapshot filter may not be null");
            this.snapshotFilter = snapshotFilter;
            return this;
        }

        /**
         * Sets the {@link EventUpcaster} used to deserialize events of older revisions. Defaults to a
         * {@link NoOpEventUpcaster}.
         * <p>
         * This object is used by the AxonServer {@link EventStorageEngine} implementation which this Builder will
         * create if it is not provided. We suggest to use these, instead of the {@link Builder#storageEngine()}
         * function to set the EventStorageEngine.
         *
         * @param upcasterChain an {@link EventUpcaster} used to deserialize events of older revisions
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder upcasterChain(EventUpcaster upcasterChain) {
            assertNonNull(upcasterChain, "EventUpcaster may not be null");
            this.upcasterChain = upcasterChain;
            return this;
        }

        /**
         * Initializes a {@link AxonServerEventStore} as specified through this Builder.
         *
         * @return a {@link AxonServerEventStore} as specified through this Builder
         */
        public AxonServerEventStore build() {
            if (storageEngine == null) {
                buildStorageEngine();
            }
            return new AxonServerEventStore(this);
        }

        private void buildStorageEngine() {
            assertNonNull(configuration, "The AxonServerConfiguration is a hard requirement and should be provided");
            assertNonNull(axonServerConnectionManager,
                          "The PlatformConnectionManager is a hard requirement and should be provided");

            AxonServerEventStoreClient eventStoreClient = new AxonServerEventStoreClient(configuration,
                                                                                         axonServerConnectionManager);
            super.storageEngine(AxonIQEventStorageEngine.builder()
                                                        .snapshotSerializer(snapshotSerializer)
                                                        .upcasterChain(upcasterChain)
                                                        .snapshotFilter(snapshotFilter)
                                                        .eventSerializer(eventSerializer)
                                                        .configuration(configuration)
                                                        .eventStoreClient(eventStoreClient)
                                                        .converter(new GrpcMetaDataConverter(eventSerializer))
                                                        .build());
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
        }
    }

    private static class AxonIQEventStorageEngine extends AbstractEventStorageEngine {

        private static final int ALLOW_SNAPSHOTS_MAGIC_VALUE = -42;
        private final String APPEND_EVENT_TRANSACTION = this + "/APPEND_EVENT_TRANSACTION";

        private final AxonServerConfiguration configuration;
        private final AxonServerEventStoreClient eventStoreClient;
        private final GrpcMetaDataConverter converter;
        private final boolean snapshotFilterSet;

        private AxonIQEventStorageEngine(Builder builder) {
            super(builder);
            this.snapshotFilterSet = builder.snapshotFilterSet;
            this.configuration = builder.configuration;
            this.eventStoreClient = builder.eventStoreClient;
            this.converter = builder.converter;
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
            AppendEventTransaction sender;
            if (CurrentUnitOfWork.isStarted()) {
                sender = CurrentUnitOfWork.get().root().getOrComputeResource(APPEND_EVENT_TRANSACTION, k -> {
                    AppendEventTransaction appendEventTransaction = eventStoreClient.createAppendEventConnection();
                    CurrentUnitOfWork.get().root().onRollback(
                            u -> appendEventTransaction.rollback(u.getExecutionResult().getExceptionResult())
                    );
                    CurrentUnitOfWork.get().root().onCommit(u -> commit(appendEventTransaction));
                    return appendEventTransaction;
                });
            } else {
                sender = eventStoreClient.createAppendEventConnection();
            }
            for (EventMessage<?> eventMessage : events) {
                sender.append(map(eventMessage, serializer));
            }
            if (!CurrentUnitOfWork.isStarted()) {
                commit(sender);
            }
        }

        private void commit(AppendEventTransaction appendEventTransaction) {
            try {
                appendEventTransaction.commit();
            } catch (ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e.getCause());
            } catch (TimeoutException e) {
                throw new org.axonframework.messaging.ExecutionException("Timeout while executing request", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        public Event map(EventMessage<?> eventMessage, Serializer serializer) {
            Event.Builder builder = Event.newBuilder();
            if (eventMessage instanceof GenericDomainEventMessage) {
                builder.setAggregateIdentifier(((GenericDomainEventMessage) eventMessage).getAggregateIdentifier())
                       .setAggregateSequenceNumber(((GenericDomainEventMessage) eventMessage).getSequenceNumber())
                       .setAggregateType(((GenericDomainEventMessage) eventMessage).getType());
            }
            SerializedObject<byte[]> serializedPayload = eventMessage.serializePayload(serializer, byte[].class);
            builder.setMessageIdentifier(eventMessage.getIdentifier()).setPayload(
                    io.axoniq.axonserver.grpc.SerializedObject.newBuilder()
                                                              .setType(serializedPayload.getType().getName())
                                                              .setRevision(getOrDefault(
                                                                      serializedPayload.getType().getRevision(),
                                                                      ""
                                                              ))
                                                              .setData(ByteString.copyFrom(serializedPayload.getData()))
            ).setTimestamp(eventMessage.getTimestamp().toEpochMilli());
            eventMessage.getMetaData().forEach((k, v) -> builder.putMetaData(k, converter.convertToMetaDataValue(v)));
            return builder.build();
        }


        @Override
        protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
            eventStoreClient.appendSnapshot(map(snapshot, serializer)).whenComplete((c, e) -> {
                if (e != null) {
                    logger.warn("Error occurred while creating a snapshot", e);
                } else if (c != null) {
                    if (c.getSuccess()) {
                        logger.info("Snapshot created");
                    } else {
                        logger.warn("Snapshot creation failed for unknown reason. Check server logs for details.");
                    }
                }
            });
        }

        @Override
        protected Stream<? extends DomainEventData<?>> readEventData(String aggregateIdentifier,
                                                                     long firstSequenceNumber) {
            logger.debug("Reading events for aggregate id {}", aggregateIdentifier);
            GetAggregateEventsRequest.Builder request = GetAggregateEventsRequest.newBuilder()
                                                                                 .setAggregateId(aggregateIdentifier);
            if (firstSequenceNumber > 0) {
                request.setInitialSequence(firstSequenceNumber);
            } else if (firstSequenceNumber == ALLOW_SNAPSHOTS_MAGIC_VALUE && !snapshotFilterSet) {
                request.setAllowSnapshots(true);
            }
            try {
                return eventStoreClient.listAggregateEvents(request.build()).map(GrpcBackedDomainEventData::new);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        public TrackingEventStream openStream(TrackingToken trackingToken) {
            Assert.isTrue(trackingToken == null || trackingToken instanceof GlobalSequenceTrackingToken,
                          () -> "Invalid tracking token type. Must be GlobalSequenceTrackingToken.");
            long nextToken = trackingToken == null
                    ? 0
                    : ((GlobalSequenceTrackingToken) trackingToken).getGlobalIndex() + 1;
            EventBuffer consumer = new EventBuffer(upcasterChain, getEventSerializer());

            logger.info("open stream: {}", nextToken);

            StreamObserver<GetEventsRequest> requestStream = eventStoreClient
                    .listEvents(new StreamObserver<EventWithToken>() {
                        @Override
                        public void onNext(EventWithToken eventWithToken) {
                            logger.debug("Received event with token: {}", eventWithToken.getToken());
                            consumer.push(eventWithToken);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            consumer.fail(new EventStoreException(
                                    "Error while reading events from the server", throwable
                            ));
                        }

                        @Override
                        public void onCompleted() {
                            consumer.fail(new EventStoreException("Error while reading events from the server",
                                                                  new RuntimeException("Connection closed by server")));
                        }
                    });
            FlowControllingStreamObserver<GetEventsRequest> observer = new FlowControllingStreamObserver<>(
                    requestStream,
                    configuration,
                    t -> GetEventsRequest.newBuilder().setNumberOfPermits(t.getPermits()).build(),
                    t -> false
            );

            GetEventsRequest request = GetEventsRequest.newBuilder()
                                                       .setTrackingToken(nextToken)
                                                       .setClientId(configuration.getClientId())
                                                       .setComponentName(configuration.getComponentName())
                                                       .setNumberOfPermits(configuration.getInitialNrOfPermits())
                                                       .build();
            observer.onNext(request);

            consumer.registerCloseListener((eventConsumer) -> observer.onCompleted());
            consumer.registerConsumeListener(observer::markConsumed);
            return consumer;
        }

        public QueryResultStream query(String query, boolean liveUpdates) {
            QueryResultBuffer consumer = new QueryResultBuffer();

            logger.debug("query: {}", query);
            StreamObserver<QueryEventsRequest> requestStream = eventStoreClient
                    .query(new StreamObserver<QueryEventsResponse>() {
                        @Override
                        public void onNext(QueryEventsResponse eventWithToken) {
                            consumer.push(eventWithToken);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            logger.info("Failed to receive events - {}", throwable.getMessage());
                            consumer.fail(new EventStoreException("Error while reading query results from the server",
                                                                  throwable));
                        }

                        @Override
                        public void onCompleted() {
                            consumer.close();
                        }
                    });
            FlowControllingStreamObserver<QueryEventsRequest> observer = new FlowControllingStreamObserver<>(
                    requestStream,
                    configuration,
                    t -> QueryEventsRequest.newBuilder().setNumberOfPermits(t.getPermits()).build(),
                    t -> false
            );

            observer.onNext(QueryEventsRequest.newBuilder()
                                              .setQuery(query)
                                              .setNumberOfPermits(configuration.getInitialNrOfPermits())
                                              .setLiveEvents(liveUpdates)
                                              .build());

            consumer.registerCloseListener((eventConsumer) -> observer.onCompleted());
            consumer.registerConsumeListener(observer::markConsumed);
            return consumer;
        }


        @Override
        public DomainEventStream readEvents(String aggregateIdentifier) {
            Stream<? extends DomainEventData<?>> input =
                    this.readEventData(aggregateIdentifier, ALLOW_SNAPSHOTS_MAGIC_VALUE);
            return DomainEventStream.of(input.map(this::upcastAndDeserializeDomainEvent).filter(Objects::nonNull));
        }

        private DomainEventMessage<?> upcastAndDeserializeDomainEvent(DomainEventData<?> domainEventData) {
            DomainEventStream upcastedStream = EventStreamUtils.upcastAndDeserializeDomainEvents(
                    Stream.of(domainEventData),
                    new GrpcMetaDataAwareSerializer(isSnapshot(domainEventData)
                                                            ? getSnapshotSerializer()
                                                            : getEventSerializer()),
                    upcasterChain

            );
            return upcastedStream.hasNext() ? upcastedStream.next() : null;
        }

        private boolean isSnapshot(DomainEventData<?> domainEventData) {
            if (domainEventData instanceof GrpcBackedDomainEventData) {
                GrpcBackedDomainEventData grpcBackedDomainEventData = (GrpcBackedDomainEventData) domainEventData;
                return grpcBackedDomainEventData.isSnapshot();
            }
            return false;
        }

        @Override
        public Optional<Long> lastSequenceNumberFor(String aggregateIdentifier) {
            try {
                ReadHighestSequenceNrResponse lastSequenceNumber = eventStoreClient
                        .lastSequenceNumberFor(aggregateIdentifier).get();
                return lastSequenceNumber.getToSequenceNr() < 0
                        ? Optional.empty()
                        : Optional.of(lastSequenceNumber.getToSequenceNr());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        @Override
        public TrackingToken createTailToken() {
            try {
                io.axoniq.axonserver.grpc.event.TrackingToken token = eventStoreClient.getFirstToken().get();
                if (token.getToken() < 0) {
                    return null;
                }
                return new GlobalSequenceTrackingToken(token.getToken() - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        @Override
        public TrackingToken createHeadToken() {
            try {
                io.axoniq.axonserver.grpc.event.TrackingToken token = eventStoreClient.getLastToken().get();
                return new GlobalSequenceTrackingToken(token.getToken());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        @Override
        public TrackingToken createTokenAt(Instant instant) {
            try {
                io.axoniq.axonserver.grpc.event.TrackingToken token = eventStoreClient.getTokenAt(instant).get();
                if (token.getToken() < 0) {
                    return null;
                }
                return new GlobalSequenceTrackingToken(token.getToken() - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        @Override
        protected Stream<? extends TrackedEventData<?>> readEventData(TrackingToken trackingToken, boolean mayBlock) {
            throw new UnsupportedOperationException(
                    "This method is not optimized for the AxonIQ Event Store and should not be used"
            );
        }

        @Override
        protected Stream<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
            if (!snapshotFilterSet) {
                // Snapshots are automatically fetched server-side, which is faster
                return Stream.empty();
            }

            return StreamSupport.stream(new Spliterators.AbstractSpliterator<DomainEventData<?>>(Long.MAX_VALUE,
                                                                                                 NONNULL | ORDERED
                                                                                                         | DISTINCT
                                                                                                         | CONCURRENT) {
                private long sequenceNumber = Long.MAX_VALUE;
                private List<DomainEventData> prefetched = new ArrayList<>();

                @Override
                public boolean tryAdvance(Consumer<? super DomainEventData<?>> action) {
                    if (prefetched.isEmpty() && sequenceNumber >= 0) {
                        GetAggregateSnapshotsRequest request = GetAggregateSnapshotsRequest.newBuilder()
                                                                                           .setAggregateId(
                                                                                                   aggregateIdentifier)
                                                                                           .setMaxResults(configuration
                                                                                                                  .getSnapshotPrefetch())
                                                                                           .setMaxSequence(
                                                                                                   sequenceNumber)
                                                                                           .build();
                        try {
                            eventStoreClient.listAggregateSnapshots(request).map(GrpcBackedDomainEventData::new)
                                            .forEach(e -> prefetched.add(e));
                        } catch (ExecutionException e) {
                            throw new EventStoreException(e.getMessage(), e);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }

                    if (prefetched.isEmpty()) {
                        return false;
                    }

                    DomainEventData<?> snapshot = prefetched.remove(0);
                    sequenceNumber = snapshot.getSequenceNumber() - 1;
                    action.accept(snapshot);
                    return true;
                }
            }, false);
        }

        private static class Builder extends AbstractEventStorageEngine.Builder {

            private boolean snapshotFilterSet;
            private AxonServerConfiguration configuration;
            private AxonServerEventStoreClient eventStoreClient;
            private GrpcMetaDataConverter converter;

            @Override
            public Builder snapshotSerializer(Serializer snapshotSerializer) {
                if (snapshotSerializer != null) {
                    super.snapshotSerializer(snapshotSerializer);
                }
                return this;
            }

            @Override
            public Builder upcasterChain(EventUpcaster upcasterChain) {
                if (upcasterChain != null) {
                    super.upcasterChain(upcasterChain);
                }
                return this;
            }

            @Override
            public Builder persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
                super.persistenceExceptionResolver(persistenceExceptionResolver);
                return this;
            }

            @Override
            public Builder eventSerializer(Serializer eventSerializer) {
                if (eventSerializer != null) {
                    super.eventSerializer(eventSerializer);
                }
                return this;
            }

            @Override
            public Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
                if (snapshotFilter != null) {
                    super.snapshotFilter(snapshotFilter);
                    snapshotFilterSet = true;
                }
                return this;
            }

            private Builder configuration(AxonServerConfiguration configuration) {
                assertNonNull(configuration, "AxonServerConfiguration may not be null");
                this.configuration = configuration;
                return this;
            }

            private Builder eventStoreClient(AxonServerEventStoreClient eventStoreClient) {
                assertNonNull(eventStoreClient, "AxonServerEventStoreClient may not be null");
                this.eventStoreClient = eventStoreClient;
                return this;
            }

            private Builder converter(GrpcMetaDataConverter converter) {
                assertNonNull(converter, "GrpcMetaDataConverter may not be null");
                this.converter = converter;
                return this;
            }

            private AxonIQEventStorageEngine build() {
                return new AxonIQEventStorageEngine(this);
            }

            @Override
            protected void validate() throws AxonConfigurationException {
                assertNonNull(configuration,
                              "The AxonServerConfiguration is a hard requirement and should be provided");
                assertNonNull(eventStoreClient,
                              "The AxonServerEventStoreClient is a hard requirement and should be provided");
                assertNonNull(converter, "The GrpcMetaDataConverter is a hard requirement and should be provided");
            }
        }
    }
}

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
import io.axoniq.axonserver.connector.event.AggregateEventStream;
import io.axoniq.axonserver.connector.event.AppendEventsTransaction;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.EventStream;
import io.axoniq.axonserver.grpc.event.Event;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.stream.BlockingStream;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.EventStreamUtils;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.AbstractEventStore;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
 * @since 4.0
 */
public class AxonServerEventStore extends AbstractEventStore {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Instantiate a Builder to be able to create a {@link AxonServerEventStore}.
     * <p>
     * The main goal of this Builder is to instantiate an AxonServer specific {@link EventStorageEngine}. The properties
     * which may be provided through this Builder are thus all used to end up with that EventStorageEngine
     * implementation. An EventStorageEngine may be provided directly however, although we encourage the usage of the
     * {@link Builder#configuration} and {@link Builder#axonServerConnectionManager} functions to let it be created.
     * <p>
     * The snapshot {@link Serializer} is defaulted to a {@link XStreamSerializer}, the event Serializer also defaults
     * to a XStreamSerializer and the {@link EventUpcaster} defaults to a {@link NoOpEventUpcaster}. The {@link
     * AxonServerConfiguration} and {@link AxonServerConnectionManager} are <b>hard requirements</b> if no
     * EventStorageEngine is provided directly.
     *
     * @return a Builder to be able to create a {@link AxonServerEventStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link AxonServerEventStore} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link EventStorageEngine} is set. If not, the {@link AxonServerConfiguration} and {@link
     * AxonServerConnectionManager} should minimally be provided to create an AxonServer specific EventStorageEngine
     * implementation. If either of these {@code null} assertions fail, an {@link AxonConfigurationException} will be
     * thrown.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AxonServerEventStore} instance
     */
    protected AxonServerEventStore(Builder builder) {
        super(builder);
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
     * Creates a {@link StreamableMessageSource} that streams events from the given {@code context}, rather than the
     * default context the application was configured to connect to.
     *
     * @param context the name of the context to create a message source for
     * @return a {@link StreamableMessageSource} of {@link TrackedEventMessage}s tied to the given {@code context}
     */
    public StreamableMessageSource<TrackedEventMessage<?>> createStreamableMessageSourceForContext(String context) {
        return new AxonServerMessageSource(storageEngine().createInstanceForContext(context));
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
     * to a XStreamSerializer and the {@link EventUpcaster} defaults to a {@link NoOpEventUpcaster}. The {@link
     * AxonServerConfiguration} and {@link AxonServerConnectionManager} are <b>hard requirements</b> if no
     * EventStorageEngine is provided directly.
     */
    public static class Builder extends AbstractEventStore.Builder {

        private AxonServerConfiguration configuration;
        private AxonServerConnectionManager axonServerConnectionManager;
        private Supplier<Serializer> snapshotSerializer = XStreamSerializer::defaultSerializer;
        private Supplier<Serializer> eventSerializer = XStreamSerializer::defaultSerializer;
        private EventUpcaster upcasterChain = NoOpEventUpcaster.INSTANCE;
        private SnapshotFilter snapshotFilter;

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
         * Sets the {@link Serializer} used to serialize and deserialize snapshots. Defaults to a {@link
         * XStreamSerializer}.
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
            this.snapshotSerializer = () -> snapshotSerializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to serialize and deserialize the Event Message's payload and Meta Data with.
         * Defaults to a {@link XStreamSerializer}.
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
            this.eventSerializer = () -> eventSerializer;
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
         * @deprecated in favor of {@link #snapshotFilter(SnapshotFilter)}
         */
        @Deprecated
        public Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
            return snapshotFilter(snapshotFilter::test);
        }

        /**
         * Sets the {@link SnapshotFilter} used to filter snapshots when returning aggregate events. When not set all
         * snapshots are used. Note that {@link SnapshotFilter} instances can be combined and should return {@code true}
         * if they handle a snapshot they wish to ignore.
         * <p>
         * This object is used by the AxonServer {@link EventStorageEngine} implementation.
         *
         * @param snapshotFilter the {@link SnapshotFilter} to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder snapshotFilter(SnapshotFilter snapshotFilter) {
            assertNonNull(snapshotFilter, "The Snapshot filter may not be null");
            this.snapshotFilter = snapshotFilter;
            return this;
        }

        /**
         * Sets the {@link EventUpcaster} used to deserialize events of older revisions. Defaults to a {@link
         * NoOpEventUpcaster}.
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

            super.storageEngine(AxonIQEventStorageEngine.builder()
                                                        .snapshotSerializer(snapshotSerializer.get())
                                                        .upcasterChain(upcasterChain)
                                                        .snapshotFilter(snapshotFilter)
                                                        .eventSerializer(eventSerializer.get())
                                                        .configuration(configuration)
                                                        .eventStoreClient(axonServerConnectionManager)
                                                        .converter(new GrpcMetaDataConverter(eventSerializer.get()))
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
        private final AxonServerConnectionManager connectionManager;
        private final GrpcMetaDataConverter converter;
        private final boolean snapshotFilterSet;
        private final Serializer snapshotSerializer;
        private final Serializer eventSerializer;

        private final Builder builder;
        private final String context;

        private static Builder builder() {
            return new Builder();
        }

        private AxonIQEventStorageEngine(Builder builder) {
            this(builder, builder.configuration.getContext());
        }

        private AxonIQEventStorageEngine(Builder builder, String context) {
            super(builder);
            this.snapshotFilterSet = builder.snapshotFilterSet;
            this.configuration = builder.configuration;
            this.connectionManager = builder.connectionManager;
            this.converter = builder.converter;

            this.builder = builder;
            this.context = context;

            this.snapshotSerializer = new GrpcMetaDataAwareSerializer(getSnapshotSerializer());
            this.eventSerializer = new GrpcMetaDataAwareSerializer(getEventSerializer());
        }

        /**
         * Creates a new AxonIQEventStorageEngine instance specifically for the given {@code context}. Can be used to
         * create multiple message sources per context if an application is required to handle events from several
         * Bounded Contexts.
         *
         * @param context a {@link String} defining the context to create an AxonIQEventStorageEngine instance
         * @return an AxonIQEventStorageEngine within the given {@code context}
         */
        private AxonIQEventStorageEngine createInstanceForContext(String context) {
            return new AxonIQEventStorageEngine(this.builder, context);
        }

        @Override
        protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
            AppendEventsTransaction sender;
            if (CurrentUnitOfWork.isStarted()) {
                sender = CurrentUnitOfWork.get().root().getOrComputeResource(APPEND_EVENT_TRANSACTION, k -> {
                    AppendEventsTransaction appendEventTransaction =
                            connectionManager.getConnection(context).eventChannel().startAppendEventsTransaction();
                    CurrentUnitOfWork.get().root().onRollback(
                            u -> appendEventTransaction.rollback()
                    );
                    CurrentUnitOfWork.get().root().onCommit(u -> commit(appendEventTransaction));
                    return appendEventTransaction;
                });
            } else {
                sender = connectionManager.getConnection(context).eventChannel().startAppendEventsTransaction();
            }
            for (EventMessage<?> eventMessage : events) {
                sender.appendEvent(map(eventMessage, serializer));
            }
            if (!CurrentUnitOfWork.isStarted()) {
                commit(sender);
            }
        }

        private void commit(AppendEventsTransaction appendEventTransaction) {
            try {
                appendEventTransaction.commit().get(configuration.getCommitTimeout(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
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
                builder.setAggregateIdentifier(((GenericDomainEventMessage<?>) eventMessage).getAggregateIdentifier())
                       .setAggregateSequenceNumber(((GenericDomainEventMessage<?>) eventMessage).getSequenceNumber())
                       .setAggregateType(((GenericDomainEventMessage<?>) eventMessage).getType());
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
            connectionManager.getConnection(context)
                             .eventChannel()
                             .appendSnapshot(map(snapshot, serializer))
                             .whenComplete((c, e) -> {
                                 if (e != null) {
                                     logger.warn("Error occurred while creating a snapshot", e);
                                 } else if (c != null) {
                                     if (c.getSuccess()) {
                                         logger.info("Snapshot created");
                                     } else {
                                         logger.warn("Snapshot creation failed for unknown reason. "
                                                             + "Check server logs for details.");
                                     }
                                 }
                             });
        }

        @Override
        protected Stream<? extends DomainEventData<?>> readEventData(String aggregateIdentifier,
                                                                     long firstSequenceNumber) {
            logger.debug("Reading events for aggregate id {}", aggregateIdentifier);
            EventChannel eventChannel = connectionManager.getConnection(context).eventChannel();

            AggregateEventStream aggregateStream;
            if (firstSequenceNumber > 0) {
                aggregateStream = eventChannel.openAggregateStream(aggregateIdentifier, firstSequenceNumber);
            } else if (firstSequenceNumber == ALLOW_SNAPSHOTS_MAGIC_VALUE && !snapshotFilterSet) {
                aggregateStream = eventChannel.openAggregateStream(aggregateIdentifier, true);
            } else {
                aggregateStream = eventChannel.openAggregateStream(aggregateIdentifier);
            }

            return aggregateStream.asStream().map(GrpcBackedDomainEventData::new);
        }

        public TrackingEventStream openStream(TrackingToken trackingToken) {
            Assert.isTrue(trackingToken == null || trackingToken instanceof GlobalSequenceTrackingToken,
                          () -> "Invalid tracking token type. Must be GlobalSequenceTrackingToken.");
            long nextToken = trackingToken == null
                    ? -1
                    : ((GlobalSequenceTrackingToken) trackingToken).getGlobalIndex();

            EventStream stream =
                    connectionManager.getConnection(context)
                                     .eventChannel()
                                     .openStream(nextToken,
                                                 configuration.getEventFlowControl().getInitialNrOfPermits(),
                                                 configuration.getEventFlowControl().getNrOfNewPermits());

            return new EventBuffer(stream, upcasterChain, eventSerializer, configuration.isDisableEventBlacklisting());
        }

        public QueryResultStream query(String query, boolean liveUpdates) {
            throw new UnsupportedOperationException("Not supported in this connector, yet");
        }

        @Override
        public DomainEventStream readEvents(String aggregateIdentifier) {
            AtomicLong lastSequenceNumber = new AtomicLong();
            Stream<? extends DomainEventData<?>> input =
                    this.readEventData(aggregateIdentifier, ALLOW_SNAPSHOTS_MAGIC_VALUE)
                        .peek(i -> lastSequenceNumber.getAndUpdate(seq -> Math.max(seq, i.getSequenceNumber())));

            return DomainEventStream.of(
                    input.flatMap(ded -> upcastAndDeserializeDomainEvent(
                            ded, isSnapshot(ded) ? snapshotSerializer : eventSerializer)
                    ).filter(Objects::nonNull),
                    lastSequenceNumber::get
            );
        }

        private Stream<? extends DomainEventMessage<?>> upcastAndDeserializeDomainEvent(
                DomainEventData<?> domainEventData,
                Serializer serializer
        ) {
            DomainEventStream upcastedStream = EventStreamUtils.upcastAndDeserializeDomainEvents(
                    Stream.of(domainEventData), serializer, upcasterChain
            );
            return upcastedStream.asStream();
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
                Long lastSequenceNumber =
                        connectionManager.getConnection(context)
                                         .eventChannel()
                                         .findHighestSequence(aggregateIdentifier)
                                         .get(configuration.getCommitTimeout(), TimeUnit.MILLISECONDS);
                return lastSequenceNumber == null || lastSequenceNumber < 0
                        ? Optional.empty()
                        : Optional.of(lastSequenceNumber);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (TimeoutException | ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        @Override
        public TrackingToken createTailToken() {
            try {
                Long token = connectionManager.getConnection(context)
                                              .eventChannel()
                                              .getFirstToken()
                                              .get(configuration.getCommitTimeout(), TimeUnit.MILLISECONDS);
                return token == null || token < 0 ? null : new GlobalSequenceTrackingToken(token-1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (TimeoutException | ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        @Override
        public TrackingToken createHeadToken() {
            try {
                Long token = connectionManager.getConnection(context)
                                              .eventChannel()
                                              .getLastToken()
                                              .get(configuration.getCommitTimeout(), TimeUnit.MILLISECONDS);
                return token == null || token < 0 ? null : new GlobalSequenceTrackingToken(token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (TimeoutException | ExecutionException e) {
                throw new EventStoreException(e.getMessage(), e);
            }
        }

        @Override
        public TrackingToken createTokenAt(Instant instant) {
            try {
                Long token = connectionManager.getConnection(context)
                                              .eventChannel()
                                              .getTokenAt(instant.toEpochMilli())
                                              .get(configuration.getCommitTimeout(), TimeUnit.MILLISECONDS);
                return token == null || token < 0 ? null : new GlobalSequenceTrackingToken(token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventStoreException(e.getMessage(), e);
            } catch (TimeoutException | ExecutionException e) {
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
                private final List<DomainEventData<byte[]>> prefetched = new ArrayList<>();

                @Override
                public boolean tryAdvance(Consumer<? super DomainEventData<?>> action) {
                    if (prefetched.isEmpty() && sequenceNumber >= 0) {
                        connectionManager.getConnection(context)
                                         .eventChannel()
                                         .loadSnapshots(aggregateIdentifier,
                                                        sequenceNumber,
                                                        configuration.getSnapshotPrefetch())
                                         .asStream()
                                         .map(GrpcBackedDomainEventData::new)
                                         .forEach(prefetched::add);
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
            private AxonServerConnectionManager connectionManager;
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

            /**
             * {@inheritDoc}
             *
             * @deprecated in favor of {@link #snapshotFilter(SnapshotFilter)}
             */
            @Override
            @Deprecated
            public Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
                if (snapshotFilter != null) {
                    super.snapshotFilter(snapshotFilter);
                    snapshotFilterSet = true;
                }
                return this;
            }

            @Override
            public Builder snapshotFilter(SnapshotFilter snapshotFilter) {
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

            private Builder eventStoreClient(AxonServerConnectionManager eventStoreClient) {
                assertNonNull(eventStoreClient, "AxonServerEventStoreClient may not be null");
                this.connectionManager = eventStoreClient;
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
                assertNonNull(connectionManager,
                              "The AxonServerEventStoreClient is a hard requirement and should be provided");
                assertNonNull(converter, "The GrpcMetaDataConverter is a hard requirement and should be provided");
            }
        }
    }

    /**
     * Wrapper around an {@link AxonIQEventStorageEngine} serving as a {@link StreamableMessageSource} of type {@link
     * TrackedEventMessage}, delegating the calls towards the provided storage engine. Can be leveraged to create new
     * StreamableMessageSources, each delegating towards a storage engine within a different Bounded Context.
     */
    private static class AxonServerMessageSource implements StreamableMessageSource<TrackedEventMessage<?>> {

        private final AxonIQEventStorageEngine eventStorageEngine;

        AxonServerMessageSource(AxonIQEventStorageEngine eventStorageEngine) {
            this.eventStorageEngine = eventStorageEngine;
        }

        @Override
        public BlockingStream<TrackedEventMessage<?>> openStream(TrackingToken trackingToken) {
            return eventStorageEngine.openStream(trackingToken);
        }

        @Override
        public TrackingToken createTailToken() {
            return eventStorageEngine.createTailToken();
        }

        @Override
        public TrackingToken createHeadToken() {
            return eventStorageEngine.createHeadToken();
        }

        @Override
        public TrackingToken createTokenAt(Instant dateTime) {
            return eventStorageEngine.createTokenAt(dateTime);
        }
    }
}

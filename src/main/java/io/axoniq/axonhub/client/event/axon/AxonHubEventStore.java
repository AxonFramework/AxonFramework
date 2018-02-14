/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.axoniq.axonhub.client.event.axon;

import com.google.protobuf.ByteString;
import io.axoniq.axondb.Event;
import io.axoniq.axondb.grpc.EventWithToken;
import io.axoniq.axondb.grpc.GetAggregateEventsRequest;
import io.axoniq.axondb.grpc.GetEventsRequest;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.util.FlowControllingStreamObserver;
import io.axoniq.axonhub.client.util.GrpcMetaDataConverter;
import io.axoniq.axonhub.client.event.AppendEventTransaction;
import io.axoniq.axonhub.client.event.AxonDBClient;
import io.grpc.stub.StreamObserver;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.*;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Axon EventStore implementation that connects to the AxonIQ AxonHub Server to store and retrieve Events.
 *
 * @author Zoltan Altfatter
 * @author Marc Gathier
 * @author Allard Buijze
 */
public class AxonHubEventStore extends AbstractEventStore {

    private static final Logger logger = LoggerFactory.getLogger(AxonHubEventStore.class);

    /**
     * Initialize the Event Store using given {@code configuration} and given {@code serializer}.
     * <p>
     * The Event Store will delay creating the connection until the first activity takes place.
     *
     * @param configuration The configuration describing the servers to connect with and how to manage flow control
     * @param platformConnectionManager
     * @param serializer    The serializer to serialize Event payloads with
     */
    public AxonHubEventStore(AxonHubConfiguration configuration, PlatformConnectionManager platformConnectionManager, Serializer serializer) {
        this(configuration, platformConnectionManager, serializer, NoOpEventUpcaster.INSTANCE);
    }

    /**
     * Initialize the Event Store using given {@code configuration}, {@code serializer} and {@code upcasterChain}
     * <p>
     * The Event Store will delay creating the connection until the first activity takes place.
     *
     * @param configuration The configuration describing the servers to connect with and how to manage flow control
     * @param platformConnectionManager
     * @param serializer    The serializer to serialize Event payloads with
     * @param upcasterChain The upcaster to modify received Event representations with
     */
    public AxonHubEventStore(AxonHubConfiguration configuration, PlatformConnectionManager platformConnectionManager,
                             Serializer serializer, EventUpcaster upcasterChain) {
        super(new AxonIQEventStorageEngine(serializer, upcasterChain, configuration, new AxonDBClient(configuration, platformConnectionManager)));
    }

    @Override
    public TrackingEventStream openStream(TrackingToken trackingToken) {
        return storageEngine().openStream(trackingToken);
    }

    @Override
    protected AxonIQEventStorageEngine storageEngine() {
        return (AxonIQEventStorageEngine) super.storageEngine();
    }

    private static class AxonIQEventStorageEngine extends AbstractEventStorageEngine {

        public static final int ALLOW_SNAPSHOTS_MAGIC_VALUE = -42;
        private final String APPEND_EVENT_TRANSACTION = this + "/APPEND_EVENT_TRANSACTION";

        private final EventUpcaster upcasterChain;
        private final AxonHubConfiguration configuration;
        private final AxonDBClient eventStoreClient;
        private final GrpcMetaDataConverter converter;

        private AxonIQEventStorageEngine(Serializer serializer,
                                         EventUpcaster upcasterChain,
                                         AxonHubConfiguration configuration,
                                         AxonDBClient eventStoreClient) {
            super(serializer, upcasterChain, null, serializer);
            this.upcasterChain = upcasterChain;
            this.configuration = configuration;
            this.eventStoreClient = eventStoreClient;
            this.converter = new GrpcMetaDataConverter(serializer);
        }

        @Override
        protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
            AppendEventTransaction sender;
            if (CurrentUnitOfWork.isStarted()) {
                sender = CurrentUnitOfWork.get().root().getOrComputeResource(APPEND_EVENT_TRANSACTION, k -> {
                    AppendEventTransaction appendEventTransaction = eventStoreClient.createAppendEventConnection();
                    CurrentUnitOfWork.get().root().onRollback(u -> appendEventTransaction.rollback(u.getExecutionResult().getExceptionResult()));
                    CurrentUnitOfWork.get().root().onCommit(u -> commit(appendEventTransaction));
                    return appendEventTransaction;
                });
            } else {
                sender = eventStoreClient.createAppendEventConnection();
            }
            for (EventMessage<?> eventMessage : events) {
                sender.append(map(eventMessage));
            }
            if (!CurrentUnitOfWork.isStarted()) {
                commit(sender);
            }
        }

        private void commit(AppendEventTransaction appendEventTransaction) {
            try {
                appendEventTransaction.commit();
            } catch (ExecutionException e) {
                throw AxonErrorMapping.convert(e.getCause());
            } catch (TimeoutException | InterruptedException e) {
                throw AxonErrorMapping.convert(e);
            }
        }

        public Event map(EventMessage eventMessage) {
            Event.Builder builder = Event.newBuilder();
            if (eventMessage instanceof GenericDomainEventMessage) {
                builder.setAggregateIdentifier(((GenericDomainEventMessage) eventMessage).getAggregateIdentifier())
                       .setAggregateSequenceNumber(((GenericDomainEventMessage) eventMessage).getSequenceNumber())
                       .setAggregateType(((GenericDomainEventMessage) eventMessage).getType());
            }
            SerializedObject<byte[]> serializedPayload = MessageSerializer.serializePayload(eventMessage, getSerializer(), byte[].class);
            builder.setMessageIdentifier(eventMessage.getIdentifier())
                   .setPayload(io.axoniq.platform.SerializedObject.newBuilder()
                                                                    .setType(serializedPayload.getType().getName())
                                                                    .setRevision(getOrDefault(serializedPayload.getType().getRevision(), ""))
                                                                    .setData(ByteString.copyFrom(serializedPayload.getData())))
                   .setTimestamp(eventMessage.getTimestamp().toEpochMilli());
            eventMessage.getMetaData().forEach((k, v) -> builder.putMetaData(k, converter.convertToMetaDataValue(v)));
            return builder.build();
        }


        @Override
        protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
            try {
                eventStoreClient.appendSnapshot(map(snapshot)).whenComplete((c, e) -> {
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
            } catch (Throwable e) {
                throw AxonErrorMapping.convert(e);
            }
        }

        @Override
        protected Stream<? extends DomainEventData<?>> readEventData(String aggregateIdentifier, long firstSequenceNumber) {
            logger.debug("Reading events for aggregate id {}", aggregateIdentifier);
            GetAggregateEventsRequest.Builder request = GetAggregateEventsRequest.newBuilder()
                                                                                 .setAggregateId(aggregateIdentifier);
            if (firstSequenceNumber > 0) {
                request.setInitialSequence(firstSequenceNumber);
            } else if (firstSequenceNumber == ALLOW_SNAPSHOTS_MAGIC_VALUE) {
                request.setAllowSnapshots(true);
            }
            try {
                return eventStoreClient.listAggregateEvents(request.build()).map(GrpcBackedDomainEventData::new);
            } catch (Exception e) {
                throw AxonErrorMapping.convert(e);
            }
        }

        public TrackingEventStream openStream(TrackingToken trackingToken) {
            Assert.isTrue(trackingToken == null || trackingToken instanceof GlobalSequenceTrackingToken,
                          () -> "Invalid tracking token type. Must be GlobalSequenceTrackingToken.");
            long nextToken = trackingToken == null ? 0 : ((GlobalSequenceTrackingToken) trackingToken).getGlobalIndex() + 1;
            EventBuffer consumer = new EventBuffer(upcasterChain, getSerializer());

            logger.info("open stream: {}", nextToken);

            StreamObserver<GetEventsRequest> requestStream = eventStoreClient.listEvents(new StreamObserver<EventWithToken>() {
                @Override
                public void onNext(EventWithToken eventWithToken) {
                    logger.debug("Received event with token: {}", eventWithToken.getToken());
                    consumer.push(eventWithToken);
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("Failed to receive events", throwable);
                    consumer.fail(new EventStoreException("Error while reading events from the server", throwable));
                }

                @Override
                public void onCompleted() {

                }
            });
            FlowControllingStreamObserver<GetEventsRequest> observer = new FlowControllingStreamObserver<>(
                    requestStream, configuration, t-> GetEventsRequest.newBuilder().setNumberOfPermits(t.getPermits()).build(), t-> false);

            GetEventsRequest request = GetEventsRequest.newBuilder()
                                                       .setTrackingToken(nextToken)
                                                        .setClient(configuration.getClientName())
                                                        .setComponent(configuration.getComponentName())
                                                       .setNumberOfPermits(configuration.getInitialNrOfPermits())
                                                       .build();
            observer.onNext(request);

            consumer.registerCloseListener((eventConsumer) -> observer.onCompleted());
            consumer.registerConsumeListener(observer::markConsumed);
            return consumer;
        }

        @Override
        public DomainEventStream readEvents(String aggregateIdentifier) {
            return readEvents(aggregateIdentifier, ALLOW_SNAPSHOTS_MAGIC_VALUE);
        }

        @Override
        protected Stream<? extends TrackedEventData<?>> readEventData(TrackingToken trackingToken, boolean mayBlock) {
            throw new UnsupportedOperationException("This method is not optimized for the AxonIQ Event Store and should not be used");
        }

        @Override
        protected Optional<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
            // snapshots are automatically fetched server-side, which is faster
            return Optional.empty();
        }

    }
}

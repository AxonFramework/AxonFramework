/*
 * Copyright (c) 2010-2019. Axon Framework
 *
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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.grpc.event.Confirmation;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventStoreGrpc;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.event.GetAggregateEventsRequest;
import io.axoniq.axonserver.grpc.event.GetAggregateSnapshotsRequest;
import io.axoniq.axonserver.grpc.event.GetEventsRequest;
import io.axoniq.axonserver.grpc.event.GetFirstTokenRequest;
import io.axoniq.axonserver.grpc.event.GetLastTokenRequest;
import io.axoniq.axonserver.grpc.event.GetTokenAtRequest;
import io.axoniq.axonserver.grpc.event.QueryEventsRequest;
import io.axoniq.axonserver.grpc.event.QueryEventsResponse;
import io.axoniq.axonserver.grpc.event.ReadHighestSequenceNrRequest;
import io.axoniq.axonserver.grpc.event.ReadHighestSequenceNrResponse;
import io.axoniq.axonserver.grpc.event.TrackingToken;
import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.AxonServerException;
import org.axonframework.axonserver.connector.event.util.EventCipher;
import org.axonframework.axonserver.connector.event.util.GrpcExceptionParser;
import org.axonframework.axonserver.connector.util.BufferingSpliterator;
import org.axonframework.axonserver.connector.util.UpstreamAwareStreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Generic client for EventStore through AxonServer. Does not require any Axon Framework specific classes.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class AxonServerEventStoreClient {

    private final Logger logger = LoggerFactory.getLogger(AxonServerEventStoreClient.class);

    private final EventCipher eventCipher;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private final int timeout;
    private final String defaultContext;
    private final int bufferCapacity;

    /**
     * Initialize the Event Store Client using given {@code eventStoreConfiguration} and given {@code
     * platformConnectionManager}.
     *
     * @param eventStoreConfiguration     The configuration describing the bounded context that this application
     *                                    operates in
     * @param axonServerConnectionManager manager for connections to AxonServer platform
     */
    public AxonServerEventStoreClient(AxonServerConfiguration eventStoreConfiguration,
                                      AxonServerConnectionManager axonServerConnectionManager) {
        this.eventCipher = eventStoreConfiguration.getEventCipher();
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.timeout = eventStoreConfiguration.getCommitTimeout();
        this.bufferCapacity = eventStoreConfiguration.getInitialNrOfPermits();
        this.defaultContext = eventStoreConfiguration.getContext();
    }

    /**
     * Shutdown the connection of this AxonServerEventStoreClient.
     *
     * @deprecated no-op method. To close connections, call {@link AxonServerConnectionManager#shutdown()} instead.
     */
    @Deprecated
    public void shutdown() {
        // No-op method.
    }

    private EventStoreGrpc.EventStoreStub eventStoreStub(String context) {
        return EventStoreGrpc.newStub(getChannelToEventStore(context));
    }

    private Channel getChannelToEventStore(String context) {
        return axonServerConnectionManager.getChannel(context);
    }

    /**
     * Retrieves the events for an aggregate described in given {@code request}.
     *
     * @param request the request describing the aggregate to retrieve messages for
     * @return a Stream providing access to Events published by the aggregate described in the request
     *
     * @deprecated in favor of {@link #listAggregateEvents(String, GetAggregateEventsRequest)}, as the {@code context}
     * should <b>always</b> be specified when dealing with an Event Store
     */
    @Deprecated
    public Stream<Event> listAggregateEvents(GetAggregateEventsRequest request) {
        return listAggregateEvents(defaultContext, request);
    }

    /**
     * Retrieves the events for an aggregate described in given {@code request}.
     *
     * @param context defines the (Bounded) Context within which a list of events for a given Aggregate should be
     *                retrieved
     * @param request the request describing the aggregate to retrieve messages for
     * @return a Stream providing access to Events published by the aggregate described in the request
     */
    public Stream<Event> listAggregateEvents(String context, GetAggregateEventsRequest request) {
        BufferingSpliterator<Event> queue = new BufferingSpliterator<>();
        StreamingEventStreamObserver responseObserver =
                new StreamingEventStreamObserver(queue, context, request.getAggregateId());
        eventStoreStub(context).listAggregateEvents(request, responseObserver);
        return StreamSupport.stream(queue, false).onClose(() -> queue.cancel(null));
    }

    /**
     * Provide a list of events by using a {@link StreamObserver} for type {@link GetEventsRequest}.
     *
     * @param responseStreamObserver a {@link StreamObserver} for messages from the server
     * @return the {@link StreamObserver} to send request messages to server with
     *
     * @deprecated in favor of {@link #listEvents(String, StreamObserver)}, as the {@code context} should <b>always</b>
     * be specified when dealing with an Event Store
     */
    @Deprecated
    public StreamObserver<GetEventsRequest> listEvents(StreamObserver<EventWithToken> responseStreamObserver) {
        return listEvents(defaultContext, responseStreamObserver);
    }

    /**
     * Provide a list of events by using a {@link StreamObserver} for type {@link GetEventsRequest}.
     *
     * @param context                defines the (Bounded) Context within which a list of events should be retrieved
     * @param responseStreamObserver a {@link StreamObserver} for messages from the server
     * @return the {@link StreamObserver} to send request messages to server with
     */
    public StreamObserver<GetEventsRequest> listEvents(String context,
                                                       StreamObserver<EventWithToken> responseStreamObserver) {
        StreamObserver<EventWithToken> wrappedStreamObserver = new StreamObserver<EventWithToken>() {
            @Override
            public void onNext(EventWithToken eventWithToken) {
                responseStreamObserver.onNext(eventCipher.decrypt(eventWithToken));
            }

            @Override
            public void onError(Throwable throwable) {
                checkConnectionException(throwable, context);
                responseStreamObserver.onError(GrpcExceptionParser.parse(throwable));
            }

            @Override
            public void onCompleted() {
                responseStreamObserver.onCompleted();
            }
        };
        return eventStoreStub(context).listEvents(wrappedStreamObserver);
    }

    /**
     * Add a snapshot event to the Event Store.
     *
     * @param snapshot the snapshot-event to store
     * @return a CompletableFuture containing the Confirmation if the operation succeeded
     *
     * @deprecated in favor of {@link #appendSnapshot(String, Event)}, as the {@code context} should <b>always</b> be
     * specified when dealing with an Event Store
     */
    @Deprecated
    public CompletableFuture<Confirmation> appendSnapshot(Event snapshot) {
        return appendSnapshot(defaultContext, snapshot);
    }

    /**
     * Add a snapshot event to the Event Store.
     *
     * @param context  defines the (Bounded) Context within which the snapshot-event should be added
     * @param snapshot the snapshot-event to store
     * @return a CompletableFuture containing the Confirmation if the operation succeeded
     */
    public CompletableFuture<Confirmation> appendSnapshot(String context, Event snapshot) {
        CompletableFuture<Confirmation> confirmationFuture = new CompletableFuture<>();
        eventStoreStub(context).appendSnapshot(
                eventCipher.encrypt(snapshot),
                new SingleResultStreamObserver<>(context, confirmationFuture)
        );
        return confirmationFuture;
    }

    /**
     * Asynchronously returns the last {@link TrackingToken} committed in Event Store.
     *
     * @return the last {@link TrackingToken} committed in Event Store asynchronously
     *
     * @deprecated in favor of {@link #getLastToken(String)}, as the {@code context} should <b>always</b> be specified
     * when dealing with an Event Store
     */
    @Deprecated
    public CompletableFuture<TrackingToken> getLastToken() {
        return getLastToken(defaultContext);
    }

    /**
     * Asynchronously returns the last {@link TrackingToken} committed in Event Store.
     *
     * @param context defines the (Bounded) Context from which the last committed TrackingToken should be retrieved
     * @return the last {@link TrackingToken} committed in Event Store asynchronously
     */
    public CompletableFuture<TrackingToken> getLastToken(String context) {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub(context).getLastToken(GetLastTokenRequest.getDefaultInstance(),
                                             new SingleResultStreamObserver<>(context, trackingTokenFuture));
        return trackingTokenFuture;
    }

    /**
     * Asynchronously returns the first {@link TrackingToken} available in Event Store.
     *
     * @return the first {@link TrackingToken} available in Event Store asynchronously
     *
     * @deprecated in favor of {@link #getFirstToken(String)}, as the {@code context} should <b>always</b> be specified
     * when dealing with an Event Store
     */
    @Deprecated
    public CompletableFuture<TrackingToken> getFirstToken() {
        return getFirstToken(defaultContext);
    }

    /**
     * Asynchronously returns the first {@link TrackingToken} available in Event Store.
     *
     * @param context defines the (Bounded) Context from which the first available TrackingToken should be retrieved
     * @return the first {@link TrackingToken} available in Event Store asynchronously
     */
    public CompletableFuture<TrackingToken> getFirstToken(String context) {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub(context).getFirstToken(GetFirstTokenRequest.getDefaultInstance(),
                                              new SingleResultStreamObserver<>(context, trackingTokenFuture));
        return trackingTokenFuture;
    }

    /**
     * Asynchronously returns a {@link TrackingToken} at the given {@code instant} from the Event Store.
     *
     * @param instant the moment in time the returned TrackingToken should correspond to
     * @return a {@link TrackingToken} at the given {@code instant} from the Event Store asynchronously
     *
     * @deprecated in favor of {@link #getTokenAt(String, Instant)}, as the {@code context} should <b>always</b> be
     * specified when dealing with an Event Store
     */
    @Deprecated
    public CompletableFuture<TrackingToken> getTokenAt(Instant instant) {
        return getTokenAt(defaultContext, instant);
    }

    /**
     * Asynchronously returns a {@link TrackingToken} at the given {@code instant} from the Event Store.
     *
     * @param context defines the (Bounded) Context from which the TrackingToken should be retrieved
     * @param instant the moment in time the returned TrackingToken should correspond to
     * @return a {@link TrackingToken} at the given {@code instant} from the Event Store asynchronously
     */
    public CompletableFuture<TrackingToken> getTokenAt(String context, Instant instant) {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub(context).getTokenAt(
                GetTokenAtRequest.newBuilder().setInstant(instant.toEpochMilli()).build(),
                new SingleResultStreamObserver<>(context, trackingTokenFuture)
        );
        return trackingTokenFuture;
    }

    /**
     * Create a transaction to append events with.
     *
     * @return a transaction to append events with.
     *
     * @deprecated in favor of {@link #createAppendEventConnection(String)}, as the {@code context} should
     * <b>always</b> be specified when dealing with an Event Store
     */
    @Deprecated
    public AppendEventTransaction createAppendEventConnection() {
        return createAppendEventConnection(defaultContext);
    }

    /**
     * Create a transaction to append events with.
     *
     * @param context defines the (Bounded) Context within which the transaction should append new events
     * @return a transaction to append events with.
     */
    public AppendEventTransaction createAppendEventConnection(String context) {
        CompletableFuture<Confirmation> futureConfirmation = new CompletableFuture<>();
        return new AppendEventTransaction(
                timeout,
                eventStoreStub(context).appendEvent(new StreamObserver<Confirmation>() {
                    @Override
                    public void onNext(Confirmation confirmation) {
                        futureConfirmation.complete(confirmation);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        checkConnectionException(throwable, context);
                        futureConfirmation.completeExceptionally(GrpcExceptionParser.parse(throwable));
                    }

                    @Override
                    public void onCompleted() {
                    }
                }),
                futureConfirmation,
                eventCipher
        );
    }

    /**
     * Query the Event Store using the given {@code responseStreamObserver} to put results on and the returned
     * {@link StreamObserver} of type {@link QueryEventsRequest} to populate with the query request.
     *
     * @param responseStreamObserver used to stream the query responses on
     * @return a stream of query request used to query the Event Store
     *
     * @deprecated in favor of {@link #query(String, StreamObserver)}, as the {@code context} should <b>always</b> be
     * specified when dealing with an Event Store
     */
    @Deprecated
    public StreamObserver<QueryEventsRequest> query(StreamObserver<QueryEventsResponse> responseStreamObserver) {
        return query(defaultContext, responseStreamObserver);
    }

    /**
     * Query the Event Store using the given {@code responseStreamObserver} to put results on and the returned
     * {@link StreamObserver} of type {@link QueryEventsRequest} to populate with the query request.
     *
     * @param context                defines the (Bounded) Context within which the query on the Event Store should be
     *                               performed
     * @param responseStreamObserver used to stream the query responses on
     * @return a stream of query request used to query the Event Store
     */
    public StreamObserver<QueryEventsRequest> query(String context,
                                                    StreamObserver<QueryEventsResponse> responseStreamObserver) {
        StreamObserver<QueryEventsResponse> wrappedStreamObserver = new StreamObserver<QueryEventsResponse>() {
            @Override
            public void onNext(QueryEventsResponse eventWithToken) {
                responseStreamObserver.onNext(eventWithToken);
            }

            @Override
            public void onError(Throwable throwable) {
                checkConnectionException(throwable, context);
                responseStreamObserver.onError(GrpcExceptionParser.parse(throwable));
            }

            @Override
            public void onCompleted() {
                responseStreamObserver.onCompleted();
            }
        };
        return eventStoreStub(context).queryEvents(wrappedStreamObserver);
    }

    private void checkConnectionException(Throwable ex, String context) {
        if (ex instanceof StatusRuntimeException &&
                ((StatusRuntimeException) ex).getStatus().getCode().equals(Status.UNAVAILABLE.getCode())) {
            stopChannelToEventStore(context);
        }
    }

    private void stopChannelToEventStore(String context) {
        axonServerConnectionManager.disconnect(context);
    }

    /**
     * Asynchronously retrieve the last sequence number used for the aggregate referenced through the given
     * {@code aggregateIdentifier}.
     *
     * @param aggregateIdentifier specifies the aggregate for which the last sequence number should be returned
     * @return the last sequence number stored for the aggregate referenced through the given {@code aggregateIdentifier}
     *
     * @deprecated in favor of {@link #lastSequenceNumberFor(String, String)}, as the {@code context} should
     * <b>always</b> be specified when dealing with an Event Store
     */
    @Deprecated
    public CompletableFuture<ReadHighestSequenceNrResponse> lastSequenceNumberFor(String aggregateIdentifier) {
        return lastSequenceNumberFor(defaultContext, aggregateIdentifier);
    }

    /**
     * Asynchronously retrieve the last sequence number used for the aggregate referenced through the given
     * {@code aggregateIdentifier}.
     *
     * @param context             defines the (Bounded) Context where the aggregate resides in, for which the last
     *                            sequence number should be returned
     * @param aggregateIdentifier specifies the aggregate for which the last sequence number should be returned
     * @return the last sequence number stored for the aggregate referenced through the given {@code aggregateIdentifier}
     */
    public CompletableFuture<ReadHighestSequenceNrResponse> lastSequenceNumberFor(String context,
                                                                                  String aggregateIdentifier) {
        CompletableFuture<ReadHighestSequenceNrResponse> completableFuture = new CompletableFuture<>();
        eventStoreStub(context).readHighestSequenceNr(
                ReadHighestSequenceNrRequest.newBuilder().setAggregateId(aggregateIdentifier).build(),
                new SingleResultStreamObserver<>(context, completableFuture)
        );
        return completableFuture;
    }

    /**
     * Retrieve a stream of all the snapshot events utilizing the given {@link GetAggregateSnapshotsRequest}
     *
     * @param request the object describing the exact snapshot event stream request
     * @return a stream of snapshot events corresponding to the given {@code request}
     *
     * @deprecated in favor of {@link #listAggregateSnapshots(String, GetAggregateSnapshotsRequest)}, as the
     * {@code context} should <b>always</b> be specified when dealing with an Event Store
     */
    @Deprecated
    public Stream<Event> listAggregateSnapshots(GetAggregateSnapshotsRequest request) {
        return listAggregateSnapshots(defaultContext, request);
    }

    /**
     * Retrieve a stream of all the snapshot events utilizing the given {@link GetAggregateSnapshotsRequest}
     *
     * @param context defines the (Bounded) Context where the aggregate resides in, for which the stream of snapshot
     *                events should be retrieved
     * @param request the object describing the exact snapshot event stream request
     * @return a stream of snapshot events corresponding to the given {@code request}
     */
    public Stream<Event> listAggregateSnapshots(String context, GetAggregateSnapshotsRequest request) {
        BufferingSpliterator<Event> queue = new BufferingSpliterator<>(bufferCapacity);
        StreamingEventStreamObserver responseObserver =
                new StreamingEventStreamObserver(queue, context, request.getAggregateId());
        eventStoreStub(context).listAggregateSnapshots(request, responseObserver);
        return StreamSupport.stream(queue, false).onClose(() -> queue.cancel(null));
    }

    /**
     * A {@link StreamObserver} implementation used to return a single result, upon which it completes the given
     * {@link CompletableFuture}.
     *
     * @param <T> the type of the single result
     */
    private class SingleResultStreamObserver<T> implements StreamObserver<T> {

        private final String context;
        private final CompletableFuture<T> future;

        private SingleResultStreamObserver(String context, CompletableFuture<T> future) {
            this.context = context;
            this.future = future;
        }

        @Override
        public void onNext(T t) {
            future.complete(t);
        }

        @Override
        public void onError(Throwable throwable) {
            checkConnectionException(throwable, context);
            future.completeExceptionally(GrpcExceptionParser.parse(throwable));
        }

        @Override
        public void onCompleted() {
            if (!future.isDone()) {
                future.completeExceptionally(new AxonServerException(
                        "AXONIQ-0001", "Async call completed before answer"
                ));
            }
        }
    }

    /**
     * A {@link StreamObserver} implementation used to return a stream of events corresponding to a specific
     * {@code aggregateId}.
     */
    private class StreamingEventStreamObserver extends UpstreamAwareStreamObserver<Event> {

        private final long before;
        private final String aggregateId;
        private final BufferingSpliterator<Event> events;
        private final String context;
        private int count;

        private StreamingEventStreamObserver(BufferingSpliterator<Event> queue,
                                             String context,
                                             String aggregateId) {
            this.context = context;
            this.before = System.currentTimeMillis();
            this.events = queue;
            this.aggregateId = aggregateId;
        }

        @Override
        public void onNext(Event event) {
            if (!events.put(eventCipher.decrypt(event))) {
                getRequestStream().cancel("Client requested cancellation", null);
            }
            count++;
        }

        @Override
        public void onError(Throwable throwable) {
            checkConnectionException(throwable, context);
            events.cancel(throwable);
        }

        @Override
        public void onCompleted() {
            events.cancel(null);
            if (logger.isDebugEnabled()) {
                logger.debug("Done request for {}: {}ms, {} events",
                             aggregateId, System.currentTimeMillis() - before, count);
            }
        }
    }
}

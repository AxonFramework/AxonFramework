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

import io.axoniq.axonserver.grpc.event.*;
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
 * Generic client for EventStore through AxonServer. Does not require any Axon framework classes.
 */
public class AxonServerEventStoreClient {
    private final Logger logger = LoggerFactory.getLogger(AxonServerEventStoreClient.class);

    private final EventCipher eventCipher;
    private final AxonServerConnectionManager axonServerConnectionManager;
    private final int timeout;
    private final String defaultContext;
    private final int bufferCapacity;

    /**
     * Initialize the Event Store Client using given {@code eventStoreConfiguration} and given {@code platformConnectionManager}.
     *
     * @param eventStoreConfiguration     The configuration describing the bounded context that this application operates in
     * @param axonServerConnectionManager manager for connections to AxonServer platform
     */
    public AxonServerEventStoreClient(AxonServerConfiguration eventStoreConfiguration, AxonServerConnectionManager axonServerConnectionManager) {
        this.eventCipher = eventStoreConfiguration.getEventCipher();
        this.axonServerConnectionManager = axonServerConnectionManager;
        this.timeout = eventStoreConfiguration.getCommitTimeout();
        this.bufferCapacity = eventStoreConfiguration.getInitialNrOfPermits();
        this.defaultContext = eventStoreConfiguration.getContext();
    }

    /**
     * @deprecated no-op method. To close connections, shutdown the connection manager
     */
    @Deprecated
    public void shutdown() {

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
     * @param request The request describing the aggregate to retrieve messages for
     * @return a Stream providing access to Events published by the aggregate described in the request
     */
    @Deprecated
    public Stream<Event> listAggregateEvents(GetAggregateEventsRequest request) {
        return listAggregateEvents(defaultContext, request);
    }

    public Stream<Event> listAggregateEvents(String context, GetAggregateEventsRequest request) {
        BufferingSpliterator<Event> queue = new BufferingSpliterator<>();
        eventStoreStub(context).listAggregateEvents(request, new StreamingEventStreamObserver(queue, context, request.getAggregateId()));
        return StreamSupport.stream(queue, false).onClose(() -> queue.cancel(null));
    }

    /**
     * Provide a list of events by using a {@link StreamObserver} for type {@link GetEventsRequest}.
     *
     * @param responseStreamObserver a {@link StreamObserver} for messages from the server
     * @return the {@link StreamObserver} to send request messages to server with
     */
    @Deprecated
    public StreamObserver<GetEventsRequest> listEvents(StreamObserver<EventWithToken> responseStreamObserver) {
        return listEvents(defaultContext, responseStreamObserver);
    }

    public StreamObserver<GetEventsRequest> listEvents(String context, StreamObserver<EventWithToken> responseStreamObserver) {
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

    @Deprecated
    public CompletableFuture<Confirmation> appendSnapshot(Event snapshot) {
        return appendSnapshot(defaultContext, snapshot);
    }

    public CompletableFuture<Confirmation> appendSnapshot(String context, Event snapshot) {
        CompletableFuture<Confirmation> confirmationFuture = new CompletableFuture<>();
        eventStoreStub(context).appendSnapshot(eventCipher.encrypt(snapshot),
                                               new SingleResultStreamObserver<>(context, confirmationFuture));
        return confirmationFuture;
    }

    @Deprecated
    public CompletableFuture<TrackingToken> getLastToken() {
        return getLastToken(defaultContext);
    }

    public CompletableFuture<TrackingToken> getLastToken(String context) {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub(context).getLastToken(GetLastTokenRequest.getDefaultInstance(),
                                             new SingleResultStreamObserver<>(context, trackingTokenFuture));
        return trackingTokenFuture;
    }

    @Deprecated
    public CompletableFuture<TrackingToken> getFirstToken() {
        return getFirstToken(defaultContext);
    }

    public CompletableFuture<TrackingToken> getFirstToken(String context) {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub(context).getFirstToken(GetFirstTokenRequest.getDefaultInstance(),
                                              new SingleResultStreamObserver<>(context, trackingTokenFuture));
        return trackingTokenFuture;
    }

    @Deprecated
    public CompletableFuture<TrackingToken> getTokenAt(Instant instant) {
        return getTokenAt(defaultContext, instant);
    }

    public CompletableFuture<TrackingToken> getTokenAt(String context, Instant instant) {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub(context).getTokenAt(GetTokenAtRequest.newBuilder()
                                                            .setInstant(instant.toEpochMilli())
                                                            .build(), new SingleResultStreamObserver<>(context, trackingTokenFuture));
        return trackingTokenFuture;
    }


    @Deprecated
    public AppendEventTransaction createAppendEventConnection() {
        return createAppendEventConnection(defaultContext);
    }

    public AppendEventTransaction createAppendEventConnection(String context) {
        CompletableFuture<Confirmation> futureConfirmation = new CompletableFuture<>();
        return new AppendEventTransaction(timeout, eventStoreStub(context).appendEvent(new StreamObserver<Confirmation>() {
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
                // no-op: already
            }
        }), futureConfirmation, eventCipher);
    }

    @Deprecated
    public StreamObserver<QueryEventsRequest> query(StreamObserver<QueryEventsResponse> responseStreamObserver) {
        return query(defaultContext, responseStreamObserver);
    }

    public StreamObserver<QueryEventsRequest> query(String context, StreamObserver<QueryEventsResponse> responseStreamObserver) {
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
        if (ex instanceof StatusRuntimeException && ((StatusRuntimeException) ex).getStatus().getCode().equals(Status.UNAVAILABLE.getCode())) {
            stopChannelToEventStore(context);
        }
    }

    private void stopChannelToEventStore(String context) {
        axonServerConnectionManager.disconnect(context);
    }

    @Deprecated
    public CompletableFuture<ReadHighestSequenceNrResponse> lastSequenceNumberFor(String aggregateIdentifier) {
        return lastSequenceNumberFor(defaultContext, aggregateIdentifier);
    }

    public CompletableFuture<ReadHighestSequenceNrResponse> lastSequenceNumberFor(String context, String aggregateIdentifier) {
        CompletableFuture<ReadHighestSequenceNrResponse> completableFuture = new CompletableFuture<>();
        eventStoreStub(context).readHighestSequenceNr(ReadHighestSequenceNrRequest.newBuilder()
                                                                                  .setAggregateId(aggregateIdentifier).build(),
                                                      new SingleResultStreamObserver<>(context, completableFuture));
        return completableFuture;
    }

    @Deprecated
    public Stream<Event> listAggregateSnapshots(GetAggregateSnapshotsRequest request) {
        return listAggregateSnapshots(defaultContext, request);
    }

    public Stream<Event> listAggregateSnapshots(String context, GetAggregateSnapshotsRequest request) {
        BufferingSpliterator<Event> queue = new BufferingSpliterator<>(bufferCapacity);
        eventStoreStub(context).listAggregateSnapshots(request, new StreamingEventStreamObserver(queue, context, request.getAggregateId()));
        return StreamSupport.stream(queue, false).onClose(() -> queue.cancel(null));
    }

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
            if (!future.isDone())
                future.completeExceptionally(new AxonServerException("AXONIQ-0001", "Async call completed before answer"));
        }
    }

    private class StreamingEventStreamObserver extends UpstreamAwareStreamObserver<Event> {
        private final long before;
        private final String aggregateId;
        private final BufferingSpliterator<Event> events;
        private final String context;
        private int count;

        public StreamingEventStreamObserver(BufferingSpliterator<Event> queue, String context, String aggregateId) {
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
                logger.debug("Done request for {}: {}ms, {} events", aggregateId, System.currentTimeMillis() - before, count);
            }
        }

    }
}

/*
 * Copyright (c) 2010-2018. Axon Framework
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

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.Confirmation;
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
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.AxonServerException;
import org.axonframework.axonserver.connector.event.util.EventCipher;
import org.axonframework.axonserver.connector.event.util.GrpcExceptionParser;
import org.axonframework.axonserver.connector.util.ContextAddingInterceptor;
import org.axonframework.axonserver.connector.util.TokenAddingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * Generic client for EventStore through AxonServer. Does not require any Axon framework classes.
 */
public class AxonServerEventStoreClient {
    private final Logger logger = LoggerFactory.getLogger(AxonServerEventStoreClient.class);

    private final TokenAddingInterceptor tokenAddingInterceptor;
    private final ContextAddingInterceptor contextAddingInterceptor;
    private final EventCipher eventCipher;
    private final AxonServerConnectionManager axonServerConnectionManager;

    private boolean shutdown;

    /**
     * Initialize the Event Store Client using given {@code eventStoreConfiguration} and given {@code platformConnectionManager}.
     *
     * @param eventStoreConfiguration The configuration describing the bounded context that this application operates in
     * @param axonServerConnectionManager manager for connections to AxonServer platform
     */
    public AxonServerEventStoreClient(AxonServerConfiguration eventStoreConfiguration, AxonServerConnectionManager axonServerConnectionManager) {
        this.tokenAddingInterceptor = new TokenAddingInterceptor(eventStoreConfiguration.getToken());
        this.eventCipher = eventStoreConfiguration.getEventCipher();
        this.axonServerConnectionManager = axonServerConnectionManager;
        contextAddingInterceptor = new ContextAddingInterceptor(eventStoreConfiguration.getContext());
    }

    public void shutdown() {
        shutdown = true;
    }

    private EventStoreGrpc.EventStoreStub eventStoreStub() {
        return EventStoreGrpc.newStub(getChannelToEventStore()).withInterceptors(tokenAddingInterceptor).withInterceptors(contextAddingInterceptor);
    }


    private Channel getChannelToEventStore() {
        if (shutdown) return null;
        return axonServerConnectionManager.getChannel();
    }

    /**
     * Retrieves the events for an aggregate described in given {@code request}.
     *
     * @param request The request describing the aggregate to retrieve messages for
     * @return a Stream providing access to Events published by the aggregate described in the request
     * @throws ExecutionException   when an error was reported while reading events
     * @throws InterruptedException when the thread was interrupted while reading events from the server
     */
    public Stream<Event> listAggregateEvents(GetAggregateEventsRequest request) throws ExecutionException, InterruptedException {
        CompletableFuture<Stream<Event>> stream = new CompletableFuture<>();
        long before = System.currentTimeMillis();

        eventStoreStub().listAggregateEvents(request, new StreamObserver<Event>() {
            Stream.Builder<Event> eventStream = Stream.builder();
            int count;

            @Override
            public void onNext(Event event) {
                eventStream.accept(eventCipher.decrypt(event));
                count++;
            }

            @Override
            public void onError(Throwable throwable) {
                checkConnectionException(throwable);
                stream.completeExceptionally(GrpcExceptionParser.parse(throwable));
            }

            @Override
            public void onCompleted() {
                stream.complete(eventStream.build());
                if (logger.isDebugEnabled()) {
                    logger.debug("Done request for {}: {}ms, {} events", request.getAggregateId(), System.currentTimeMillis() - before, count);
                }
            }
        });
        return stream.get();
    }

    /**
     *
     * @param responseStreamObserver: observer for messages from server
     * @return stream observer to send request messages to server
     */
    public StreamObserver<GetEventsRequest> listEvents(StreamObserver<EventWithToken> responseStreamObserver) {
        StreamObserver<EventWithToken> wrappedStreamObserver = new StreamObserver<EventWithToken>() {
            @Override
            public void onNext(EventWithToken eventWithToken) {
                responseStreamObserver.onNext(eventCipher.decrypt(eventWithToken));
            }

            @Override
            public void onError(Throwable throwable) {
                checkConnectionException(throwable);
                responseStreamObserver.onError(GrpcExceptionParser.parse(throwable));
            }

            @Override
            public void onCompleted() {
                responseStreamObserver.onCompleted();

            }
        };
        return eventStoreStub().listEvents(wrappedStreamObserver);
    }

    public CompletableFuture<Confirmation> appendSnapshot(Event snapshot) {
        CompletableFuture<Confirmation> confirmationFuture = new CompletableFuture<>();
        eventStoreStub().appendSnapshot(eventCipher.encrypt(snapshot),
                                        new SingleResultStreamObserver<>(confirmationFuture ));


        return confirmationFuture;
    }

    public CompletableFuture<TrackingToken> getLastToken() {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub().getLastToken(GetLastTokenRequest.getDefaultInstance(),
                                      new SingleResultStreamObserver<>(trackingTokenFuture));
        return trackingTokenFuture;
    }

    public CompletableFuture<TrackingToken> getFirstToken() {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub().getFirstToken(GetFirstTokenRequest.getDefaultInstance(),
                                       new SingleResultStreamObserver<>(trackingTokenFuture));
        return trackingTokenFuture;
    }

    public CompletableFuture<TrackingToken> getTokenAt(Instant instant) {
        CompletableFuture<TrackingToken> trackingTokenFuture = new CompletableFuture<>();
        eventStoreStub().getTokenAt(GetTokenAtRequest.newBuilder()
                                                     .setInstant(instant.toEpochMilli())
                                                     .build(), new SingleResultStreamObserver<>(trackingTokenFuture));
        return trackingTokenFuture;
    }


    public AppendEventTransaction createAppendEventConnection() {
        CompletableFuture<Confirmation> futureConfirmation = new CompletableFuture<>();
        return new AppendEventTransaction(eventStoreStub().appendEvent(new StreamObserver<Confirmation>() {
            @Override
            public void onNext(Confirmation confirmation) {
                futureConfirmation.complete(confirmation);
            }

            @Override
            public void onError(Throwable throwable) {
                checkConnectionException(throwable);
                futureConfirmation.completeExceptionally(GrpcExceptionParser.parse(throwable));
            }

            @Override
            public void onCompleted() {
                // no-op: already
            }
        }), futureConfirmation, eventCipher);
    }

    public StreamObserver<QueryEventsRequest> query(StreamObserver<QueryEventsResponse> responseStreamObserver) {
        StreamObserver<QueryEventsResponse> wrappedStreamObserver = new StreamObserver<QueryEventsResponse>() {
            @Override
            public void onNext(QueryEventsResponse eventWithToken) {
                responseStreamObserver.onNext(eventWithToken);
            }

            @Override
            public void onError(Throwable throwable) {
                checkConnectionException(throwable);
                responseStreamObserver.onError(GrpcExceptionParser.parse(throwable));
            }

            @Override
            public void onCompleted() {
                responseStreamObserver.onCompleted();

            }
        };
        return eventStoreStub().queryEvents(wrappedStreamObserver);
    }

    private void checkConnectionException(Throwable ex) {
        if (ex instanceof StatusRuntimeException && ((StatusRuntimeException) ex).getStatus().getCode().equals(Status.UNAVAILABLE.getCode())) {
            stopChannelToEventStore();
        }
    }

    private void stopChannelToEventStore() {

    }

    public CompletableFuture<ReadHighestSequenceNrResponse> lastSequenceNumberFor(String aggregateIdentifier) {
        CompletableFuture<ReadHighestSequenceNrResponse> completableFuture = new CompletableFuture<>();
        eventStoreStub().readHighestSequenceNr(ReadHighestSequenceNrRequest.newBuilder()
                                                                           .setAggregateId(aggregateIdentifier).build(),
                                               new SingleResultStreamObserver<>(completableFuture));
        return completableFuture;
    }

    public Stream<Event> listAggregateSnapshots(GetAggregateSnapshotsRequest request)
            throws ExecutionException, InterruptedException {
        CompletableFuture<Stream<Event>> stream = new CompletableFuture<>();
        long before = System.currentTimeMillis();

        eventStoreStub().listAggregateSnapshots(request, new StreamObserver<Event>() {
            Stream.Builder<Event> eventStream = Stream.builder();
            int count;

            @Override
            public void onNext(Event event) {
                eventStream.accept(eventCipher.decrypt(event));
                count++;
            }

            @Override
            public void onError(Throwable throwable) {
                checkConnectionException(throwable);
                stream.completeExceptionally(GrpcExceptionParser.parse(throwable));
            }

            @Override
            public void onCompleted() {
                stream.complete(eventStream.build());
                if (logger.isDebugEnabled()) {
                    logger.debug("Done request for {}: {}ms, {} events", request.getAggregateId(), System.currentTimeMillis() - before, count);
                }
            }
        });
        return stream.get();
    }

    private class SingleResultStreamObserver<T> implements StreamObserver<T> {
        private final CompletableFuture<T> future;

        private SingleResultStreamObserver(CompletableFuture<T> future) {
            this.future = future;
        }

        @Override
        public void onNext(T t) {
            future.complete(t);
        }

        @Override
        public void onError(Throwable throwable) {
            checkConnectionException(throwable);
            future.completeExceptionally(GrpcExceptionParser.parse(throwable));
        }

        @Override
        public void onCompleted() {
            if( ! future.isDone()) future.completeExceptionally(new AxonServerException("AXONIQ-0001", "Async call completed before answer"));
        }
    }
}

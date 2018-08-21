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

package io.axoniq.axonhub.client.event;

import io.axoniq.axondb.Event;
import io.axoniq.axondb.grpc.Confirmation;
import io.axoniq.axondb.grpc.EventStoreGrpc;
import io.axoniq.axondb.grpc.EventWithToken;
import io.axoniq.axondb.grpc.GetAggregateEventsRequest;
import io.axoniq.axondb.grpc.GetEventsRequest;
import io.axoniq.axondb.grpc.GetFirstTokenRequest;
import io.axoniq.axondb.grpc.GetLastTokenRequest;
import io.axoniq.axondb.grpc.GetTokenAtRequest;
import io.axoniq.axondb.grpc.QueryEventsRequest;
import io.axoniq.axondb.grpc.QueryEventsResponse;
import io.axoniq.axondb.grpc.ReadHighestSequenceNrRequest;
import io.axoniq.axondb.grpc.ReadHighestSequenceNrResponse;
import io.axoniq.axondb.grpc.TrackingToken;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.AxonHubException;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.event.util.EventCipher;
import io.axoniq.axonhub.client.event.util.GrpcExceptionParser;
import io.axoniq.axonhub.client.util.ContextAddingInterceptor;
import io.axoniq.axonhub.client.util.TokenAddingInterceptor;
import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * Generic client for EventStore through AxonHub. Does not require any Axon framework classes.
 */
public class AxonDBClient {
    private final Logger logger = LoggerFactory.getLogger(AxonDBClient.class);

    private final TokenAddingInterceptor tokenAddingInterceptor;
    private final ContextAddingInterceptor contextAddingInterceptor;
    private final EventCipher eventCipher;
    private final PlatformConnectionManager platformConnectionManager;

    private boolean shutdown;

    public AxonDBClient(AxonHubConfiguration eventStoreConfiguration, PlatformConnectionManager platformConnectionManager) {
        this.tokenAddingInterceptor = new TokenAddingInterceptor(eventStoreConfiguration.getToken());
        this.eventCipher = eventStoreConfiguration.getEventCipher();
        this.platformConnectionManager = platformConnectionManager;
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
        return platformConnectionManager.getChannel();
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
            if( ! future.isDone()) future.completeExceptionally(new AxonHubException("AXONIQ-0001", "Async call completed before answer"));
        }
    }
}

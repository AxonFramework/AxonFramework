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

package io.axoniq.axonhub.client.query.subscription;

import io.axoniq.axonhub.SubscriptionQuery;
import io.axoniq.axonhub.SubscriptionQueryResponse;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.util.FlowControllingStreamObserver;
import io.axoniq.axonhub.grpc.FlowControl;
import io.axoniq.axonhub.grpc.QueryServiceGrpc;
import io.axoniq.axonhub.grpc.SubscriptionQueryOutbound;
import io.grpc.stub.StreamObserver;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.axoniq.axonhub.grpc.SubscriptionQueryOutbound.newBuilder;

/**
 *
 * SubscriptionQueryResult that emits initial response and update when subscription query response message is received.
 *
 * @author Sara Pellegrini
 */
class AxonHubSubscriptionQueryResult<Q, I, U> implements
        Supplier<SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>>>,
        StreamObserver<SubscriptionQueryResponse> {

    private final Logger logger = LoggerFactory.getLogger(AxonHubSubscriptionQueryResult.class);

    private final FlowControllingStreamObserver<SubscriptionQueryOutbound> requestObserver;

    private final SubscriptionMessageSerializer serializer;

    private final Mono<QueryResponseMessage<I>> initialResult;

    private final Flux<SubscriptionQueryUpdateMessage<U>> updates;

    private final SubscriptionQuery subscriptionQuery;

    private final Consumer<Integer> responseCounterConsumer;

    private MonoSink<QueryResponseMessage<I>> initialResultSink;

    private FluxSink<SubscriptionQueryUpdateMessage<U>> updateMessageFluxSink;

    AxonHubSubscriptionQueryResult(
            SubscriptionQueryMessage<Q, I, U> query,
            QueryServiceGrpc.QueryServiceStub queryService,
            AxonHubConfiguration configuration,
            SubscriptionMessageSerializer serializer,
            SubscriptionQueryBackpressure backpressure) {
        this.subscriptionQuery = serializer.serialize(query);
        this.serializer = serializer;
        this.initialResult = Mono.create(this::initialResult);
        this.updates = Flux.create(this::updates, backpressure.getOverflowStrategy());
        StreamObserver<SubscriptionQueryOutbound> subscription = queryService.subscription(this);
        Function<FlowControl, SubscriptionQueryOutbound> requestMapping = flowControl ->
                newBuilder().setFlowControl(SubscriptionQuery.newBuilder(subscriptionQuery).setNumberOfPermits(flowControl.getPermits())).build();
        requestObserver = new FlowControllingStreamObserver<>(subscription, configuration, requestMapping, t -> false);
        responseCounterConsumer = requestObserver::markConsumed;
    }

    private void initialResult(MonoSink<QueryResponseMessage<I>> monoSink){
        initialResultSink = monoSink;
        requestObserver.onNext(newBuilder().setSubscribeInitial(subscriptionQuery).build());
    }

    private void updates(FluxSink<SubscriptionQueryUpdateMessage<U>> fluxSink){
        updateMessageFluxSink = fluxSink;
        fluxSink.onDispose(() -> Optional.ofNullable(requestObserver).ifPresent(StreamObserver::onCompleted));
        requestObserver.onNext(newBuilder().setSubscribeInitial(subscriptionQuery).build());
    }

    @Override
    public void onNext(SubscriptionQueryResponse response) {
        responseCounterConsumer.accept(1);
        switch (response.getResponseCase()) {
            case INITIAL_RESPONSE:
                logger.debug("Initial response received: {}", response);
                initialResultSink.success(serializer.deserialize(response.getInitialResponse()));
                break;
            case UPDATE:
                logger.debug("Update received: {}", response);
                updateMessageFluxSink.next(serializer.deserialize(response.getUpdate()));
                break;
            case COMPLETE:
                updateMessageFluxSink.complete();
                break;
            case COMPLETE_EXCEPTIONALLY:
                logger.debug("Received complete exceptionally subscription query: {}", response);
                updateMessageFluxSink.error(serializer.deserialize(response.getCompleteExceptionally()));
                break;
        }
    }

    @Override
    public void onError(Throwable t) {
        initialResultSink.error(t);
        updateMessageFluxSink.error(t);
    }

    @Override
    public void onCompleted() {
        initialResultSink.success();
        updateMessageFluxSink.complete();
    }

    @Override
    public SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> get() {
        return new DefaultSubscriptionQueryResult<>(initialResult, updates);
    }
}

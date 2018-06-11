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

import io.axoniq.axonhub.SubscriptionQueryResponse;
import io.grpc.stub.StreamObserver;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.Optional;
import java.util.function.Consumer;

/**
 *
 * SubscriptionQueryResult that emits initial response and update when subscription query response message is received.
 *
 * @author Sara Pellegrini
 */
class AxonHubSubscriptionQueryResult<I, U> implements
        SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>>,
        StreamObserver<SubscriptionQueryResponse> {

    private final Logger logger = LoggerFactory.getLogger(AxonHubSubscriptionQueryResult.class);

    private final SubscriptionMessageSerializer serializer;

    private final Mono<QueryResponseMessage<I>> initialResult;

    private final Flux<SubscriptionQueryUpdateMessage<U>> updates;

    private MonoSink<QueryResponseMessage<I>> initialResultSink;

    private FluxSink<SubscriptionQueryUpdateMessage<U>> updateMessageFluxSink;

    private Disposable disposable;

    private Consumer<Integer> responseCounterConsumer = i -> {};

    AxonHubSubscriptionQueryResult(
            SubscriptionMessageSerializer serializer,
            SubscriptionQueryBackpressure backpressure) {
        this.serializer = serializer;
        this.initialResult = Mono.create(sink -> initialResultSink = sink);
        this.updates = Flux.create(sink -> {
            updateMessageFluxSink = sink;
            sink.onDispose(() -> Optional.ofNullable(disposable).ifPresent(Disposable::dispose));
        }, backpressure.getOverflowStrategy());
    }


    @Override
    public Mono<QueryResponseMessage<I>> initialResult() {
        return initialResult;
    }

    @Override
    public Flux<SubscriptionQueryUpdateMessage<U>> updates() {
        return updates;
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

    void onDispose(Disposable disposable) {
        this.disposable = disposable;
    }

    void onResponse(Consumer<Integer> responseCounterConsumer){
        this.responseCounterConsumer = responseCounterConsumer;
    }
}

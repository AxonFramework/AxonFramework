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
import io.axoniq.axonhub.SubscriptionQueryRequest;
import io.axoniq.axonhub.SubscriptionQueryResponse;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.Publisher;
import io.axoniq.axonhub.client.util.FlowControllingStreamObserver;
import io.axoniq.axonhub.grpc.FlowControl;
import io.axoniq.axonhub.grpc.QueryServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.axonframework.common.Registration;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.axoniq.axonhub.SubscriptionQueryRequest.newBuilder;

/**
 *
 * SubscriptionQueryResult that emits initial response and update when subscription query response message is received.
 *
 * @author Sara Pellegrini
 */
class AxonHubSubscriptionQueryResult<Q, I, U> implements
        Supplier<SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>>>,
        StreamObserver<SubscriptionQueryResponse> {

    private final FlowControllingStreamObserver<SubscriptionQueryRequest> requestObserver;

    private final SubscriptionMessageSerializer serializer;

    private final SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> result;

    private final SubscriptionQuery subscriptionQuery;

    private final FluxSink<SubscriptionQueryUpdateMessage<U>> updateMessageFluxSink;

    private MonoSink<QueryResponseMessage<I>> initialResultSink;

    AxonHubSubscriptionQueryResult(
            SubscriptionQueryMessage<Q, I, U> query,
            QueryServiceGrpc.QueryServiceStub queryService,
            AxonHubConfiguration configuration,
            SubscriptionMessageSerializer serializer,
            SubscriptionQueryBackpressure backPressure,
            int bufferSize) {

        EmitterProcessor<SubscriptionQueryUpdateMessage<U>> processor = EmitterProcessor.create(bufferSize);

        this.subscriptionQuery = serializer.serialize(query);
        this.serializer = serializer;
        this.updateMessageFluxSink = processor.sink(backPressure.getOverflowStrategy());

        StreamObserver<SubscriptionQueryRequest> subscription = queryService.subscription(this);
        Function<FlowControl, SubscriptionQueryRequest> requestMapping = flowControl ->
                newBuilder().setFlowControl(SubscriptionQuery.newBuilder(subscriptionQuery).setNumberOfPermits(flowControl.getPermits())).build();
        requestObserver = new FlowControllingStreamObserver<>(subscription, configuration, requestMapping, t -> false);
        requestObserver.onNext(newBuilder().setSubscribe(subscriptionQuery).build());
        updateMessageFluxSink.onDispose(requestObserver::onCompleted);
        Registration registration = () -> {
            updateMessageFluxSink.complete();
            return true;
        };
        Mono<QueryResponseMessage<I>> mono = Mono.create(sink -> initialResult(sink, requestObserver::onNext));
        this.result = new DefaultSubscriptionQueryResult<>(mono,  processor.replay().autoConnect(), registration);
    }

    private void initialResult(MonoSink<QueryResponseMessage<I>> sink, Publisher<SubscriptionQueryRequest> publisher){
        initialResultSink = sink;
        publisher.publish(newBuilder().setGetInitialResult(subscriptionQuery).build());
    }


    @Override
    public void onNext(SubscriptionQueryResponse response) {
        requestObserver.markConsumed(1);
        switch (response.getResponseCase()) {
            case INITIAL_RESPONSE:
                initialResultSink.success(serializer.deserialize(response.getInitialResponse()));
                break;
            case UPDATE:
                updateMessageFluxSink.next(serializer.deserialize(response.getUpdate()));
                break;
            case COMPLETE:
                onCompleted();
                break;
            case COMPLETE_EXCEPTIONALLY:
                Throwable e = serializer.deserialize(response.getCompleteExceptionally());
                onError(e);
                break;
        }
    }

    @Override
    public void onError(Throwable t) {
        Optional.ofNullable(initialResultSink).ifPresent(sink -> sink.error(t));
        updateMessageFluxSink.error(t);
    }

    @Override
    public void onCompleted() {
        Optional.ofNullable(initialResultSink).ifPresent(
                sink -> sink.error(new IllegalStateException("Subscription Completed")));
        updateMessageFluxSink.complete();
    }

    @Override
    public SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> get() {
        return this.result;
    }
}

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

package org.axonframework.axonserver.connector.query.subscription;

import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.QueryUpdateCompleteExceptionally;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryRequest;
import io.axoniq.axonserver.grpc.query.SubscriptionQueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.Publisher;
import org.axonframework.axonserver.connector.query.AxonServerRemoteQueryHandlingException;
import org.axonframework.axonserver.connector.util.FlowControllingStreamObserver;
import io.axoniq.axonserver.grpc.FlowControl;
import io.grpc.stub.StreamObserver;
import org.axonframework.common.Registration;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.SubscriptionQueryBackpressure;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.function.Function;
import java.util.function.Supplier;

import static io.axoniq.axonserver.grpc.query.SubscriptionQueryRequest.newBuilder;
import static java.util.Optional.ofNullable;

/**
 * SubscriptionQueryResult that emits initial response and update when subscription query response message is received.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class AxonServerSubscriptionQueryResult implements
        Supplier<SubscriptionQueryResult<QueryResponse, QueryUpdate>>,
        StreamObserver<SubscriptionQueryResponse> {

    private final Logger logger = LoggerFactory.getLogger(AxonServerSubscriptionQueryResult.class);

    private final FlowControllingStreamObserver<SubscriptionQueryRequest> requestObserver;

    private final SubscriptionQueryResult<QueryResponse, QueryUpdate> result;

    private final SubscriptionQuery subscriptionQuery;

    private final FluxSink<QueryUpdate> updateMessageFluxSink;

    private final Runnable onDispose;

    private MonoSink<QueryResponse> initialResultSink;

    public AxonServerSubscriptionQueryResult(
            SubscriptionQuery query,
            Function<StreamObserver<SubscriptionQueryResponse>, StreamObserver<SubscriptionQueryRequest>> openStreamFn,
            AxonServerConfiguration configuration,
            SubscriptionQueryBackpressure backPressure,
            int bufferSize, Runnable onDispose) {
        this.onDispose = onDispose;
        this.subscriptionQuery = query;
        EmitterProcessor<QueryUpdate> processor = EmitterProcessor.create(bufferSize);
        this.updateMessageFluxSink = processor.sink(backPressure.getOverflowStrategy());
        StreamObserver<SubscriptionQueryRequest> subscription = openStreamFn.apply(this);
        Function<FlowControl, SubscriptionQueryRequest> requestMapping = flowControl ->
                newBuilder().setFlowControl(SubscriptionQuery.newBuilder(subscriptionQuery)
                                                             .setNumberOfPermits(flowControl.getPermits())).build();
        requestObserver = new FlowControllingStreamObserver<>(subscription, configuration, requestMapping, t -> false);
        requestObserver.sendInitialPermits();
        requestObserver.onNext(newBuilder().setSubscribe(subscriptionQuery).build());
        updateMessageFluxSink.onDispose(requestObserver::onCompleted);
        Registration registration = () -> {
            updateMessageFluxSink.complete();
            return true;
        };
        Mono<QueryResponse> mono = Mono.create(sink -> initialResult(sink, requestObserver::onNext));
        this.result = new DefaultSubscriptionQueryResult<>(mono, processor.replay().autoConnect(), registration);
    }

    private void initialResult(MonoSink<QueryResponse> sink, Publisher<SubscriptionQueryRequest> publisher) {
        initialResultSink = sink;
        publisher.publish(newBuilder().setGetInitialResult(subscriptionQuery).build());
    }


    @Override
    public void onNext(SubscriptionQueryResponse response) {
        requestObserver.markConsumed(1);
        switch (response.getResponseCase()) {
            case INITIAL_RESULT:
                ofNullable(initialResultSink).ifPresent(sink -> sink.success(response.getInitialResult()));
                break;
            case UPDATE:
                updateMessageFluxSink.next(response.getUpdate());
                break;
            case COMPLETE:
                requestObserver.onCompleted();
                complete();
                break;
            case COMPLETE_EXCEPTIONALLY:
                requestObserver.onCompleted();
                QueryUpdateCompleteExceptionally exceptionally = response.getCompleteExceptionally();
                Throwable e = new AxonServerRemoteQueryHandlingException(exceptionally.getErrorCode(),
                                                                         exceptionally.getErrorMessage());
                completeExceptionally(e);
                break;
        }
    }

    @Override
    public void onError(Throwable t) {
        completeExceptionally(t);
    }

    @Override
    public void onCompleted() {
        complete();
    }

    @Override
    public SubscriptionQueryResult<QueryResponse, QueryUpdate> get() {
        return this.result;
    }


    private void complete() {
        onDispose.run();
        updateMessageFluxSink.complete();
        initialResultError(new IllegalStateException("Subscription Completed"));
    }

    private void completeExceptionally(Throwable t) {
        onDispose.run();
        updateError(t);
        initialResultError(t);
    }

    private void updateError(Throwable t){
        try {
            updateMessageFluxSink.error(t);
        } catch (Exception e) {
            updateMessageFluxSink.complete();
            logger.warn("Problem signaling updates error.", e);
        }
    }

    private void initialResultError(Throwable t){
        try {
            ofNullable(initialResultSink).ifPresent(sink -> sink.error(t));
        } catch (Exception e) {
            logger.warn("Problem signaling initial result error.", e);
        }
    }

}

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

package org.axonframework.queryhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.backpressure.BackpressuredUpdateHandler;
import org.axonframework.queryhandling.backpressure.TimeBasedBackpressure;
import org.axonframework.queryhandling.responsetypes.ResponseType;
import org.axonframework.queryhandling.responsetypes.ResponseTypes;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Default implementation of {@link ReactiveQueryGateway}.
 *
 * @author Milan Savic
 * @since 5.0
 */
public class DefaultReactiveQueryGateway implements ReactiveQueryGateway {

    private final QueryBus queryBus;
    private final MessageDispatchInterceptor<? super QueryMessage<?, ?>>[] dispatchInterceptors;

    /**
     * Initializes the gateway to send queries to the given {@code queryBus} and invoking given {@code
     * dispatchInterceptors} prior to publication on the query bus.
     *
     * @param queryBus             The bus to deliver messages on
     * @param dispatchInterceptors The interceptors to invoke prior to publication on the bus
     */
    @SafeVarargs
    public DefaultReactiveQueryGateway(QueryBus queryBus,
                                       MessageDispatchInterceptor<? super QueryMessage<?, ?>>... dispatchInterceptors) {
        this.queryBus = queryBus;
        this.dispatchInterceptors = dispatchInterceptors;
    }

    @Override
    public <R, Q> Mono<R> query(String queryName, Q query, ResponseType<R> responseType) {
        GenericQueryMessage<Q, R> queryMessage =
                processInterceptors(new GenericQueryMessage<>(query, queryName, responseType));
        return Mono.fromFuture(queryBus.query(queryMessage).thenApply(QueryResponseMessage::getPayload));
    }

    @Override
    public <R, Q> Flux<R> scatterGather(String queryName, Q query, ResponseType<R> responseType, long timeout,
                                        TimeUnit timeUnit) {
        GenericQueryMessage<Q, R> queryMessage =
                processInterceptors(new GenericQueryMessage<>(query, queryName, responseType));
        return Flux.fromStream(queryBus.scatterGather(queryMessage, timeout, timeUnit)
                                       .map(QueryResponseMessage::getPayload));
    }

    @Override
    public <Q, R> Flux<R> subscriptionQuery(String queryName, Q query, Class<R> responseType,
                                            FluxSink.OverflowStrategy overflowStrategy,
                                            Function<UpdateHandler<List<R>, R>, BackpressuredUpdateHandler<List<R>, R>> backpressureBuilder) {
        SubscriptionQueryMessage<Q, List<R>, R> subscriptionQueryMessage =
                processInterceptors(new GenericSubscriptionQueryMessage<>(query,
                                                                          queryName,
                                                                          ResponseTypes
                                                                                  .multipleInstancesOf(responseType),
                                                                          ResponseTypes.instanceOf(responseType)));
        return Flux.create(emitter -> {
            ReactiveUpdateHandler<R> fluxUpdateHandler = new ReactiveUpdateHandler<>(emitter);
            BackpressuredUpdateHandler<List<R>, R> backpressuredUpdateHandler = backpressureBuilder.apply(
                    fluxUpdateHandler);
            Registration registration = queryBus.subscriptionQuery(subscriptionQueryMessage,
                                                                   backpressuredUpdateHandler);
            emitter.onDispose(() -> {
                registration.cancel();
                if (backpressuredUpdateHandler instanceof TimeBasedBackpressure) {
                    ((TimeBasedBackpressure<List<R>, R>) backpressuredUpdateHandler).shutdown();
                }
            });
        }, overflowStrategy);
    }

    @SuppressWarnings("unchecked")
    private <Q, R, T extends QueryMessage<Q, R>> T processInterceptors(T query) {
        T intercepted = query;
        for (MessageDispatchInterceptor<? super QueryMessage<?, ?>> interceptor : dispatchInterceptors) {
            intercepted = (T) interceptor.handle(intercepted);
        }
        return intercepted;
    }

    private static class ReactiveUpdateHandler<T> implements UpdateHandler<List<T>, T> {

        private final FluxSink<T> emitter;

        private ReactiveUpdateHandler(FluxSink<T> emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onInitialResult(List<T> initial) {
            initial.forEach(emitter::next);
        }

        @Override
        public void onUpdate(T update) {
            emitter.next(update);
        }

        @Override
        public void onCompleted() {
            emitter.complete();
        }

        @Override
        public void onCompletedExceptionally(Throwable error) {
            emitter.error(error);
        }
    }
}

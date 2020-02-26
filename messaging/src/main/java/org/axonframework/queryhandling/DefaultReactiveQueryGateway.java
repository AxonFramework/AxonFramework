/*
 * Copyright (c) 2010-2020. Axon Framework
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
import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ReactiveMessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.ResponseType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link ReactiveQueryGateway}.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class DefaultReactiveQueryGateway implements ReactiveQueryGateway {

    private final List<ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>>> dispatchInterceptors = new CopyOnWriteArrayList<>();

    private final QueryBus queryBus;

    /**
     * Creates an instance of {@link DefaultReactiveQueryGateway}.
     *
     * @param queryBus used for query dispatching
     */
    public DefaultReactiveQueryGateway(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    /**
     * Registers a {@link ReactiveMessageDispatchInterceptor} within this reactive gateway.
     *
     * @param interceptor intercepts a query message
     * @return a registration which can be used to unregister this {@code interceptor}
     */
    public Registration registerQueryDispatchInterceptor(
            ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    private Mono<QueryMessage<?, ?>> processInterceptors(Mono<QueryMessage<?, ?>> queryMessage) {
        Mono<QueryMessage<?, ?>> message = queryMessage;
        for (ReactiveMessageDispatchInterceptor<QueryMessage<?, ?>> dispatchInterceptor : dispatchInterceptors) {
            try {
                message = dispatchInterceptor.intercept(message);
            } catch (Throwable t) {
                return Mono.error(t);
            }
        }
        return message;
    }

    @Override
    public <R, Q> Mono<R> query(String queryName, Mono<Q> query, ResponseType<R> responseType) {
        return processInterceptors(query.map(q -> new GenericQueryMessage<>(q, queryName, responseType)))
                .flatMap(queryMessage -> Mono.create(
                        sink -> queryBus.query(queryMessage).whenComplete((response, throwable) -> {
                            try {
                                if (throwable != null) {
                                    sink.error(throwable);
                                } else {
                                    if (response.isExceptional()) {
                                        sink.error(response.exceptionResult());
                                    } else {
                                        sink.success(((QueryResponseMessage<R>) response).getPayload());
                                    }
                                }
                            } catch (Exception e) {
                                sink.error(e);
                            }
                        })
                ));
    }

    @Override
    public <R, Q> Flux<R> scatterGather(String queryName, Mono<Q> query, ResponseType<R> responseType, long timeout,
                                        TimeUnit timeUnit) {
        return processInterceptors(query.map(q -> new GenericQueryMessage<>(q, queryName, responseType)))
                .flatMapMany(queryMessage -> Flux.create(
                        sink -> {
                            queryBus.scatterGather((QueryMessage<?, R>) queryMessage, timeout, timeUnit)
                                    .map(Message::getPayload)
                                    .forEach(sink::next);
                            sink.complete();
                        }
                ));
    }

    @Override
    public <Q, I, U> Mono<SubscriptionQueryResult<I, U>> subscriptionQuery(String queryName, Mono<Q> query,
                                                                           ResponseType<I> initialResponseType,
                                                                           ResponseType<U> updateResponseType,
                                                                           SubscriptionQueryBackpressure backpressure,
                                                                           int updateBufferSize) {
        return processInterceptors(query.map(q -> new GenericSubscriptionQueryMessage<>(q,
                                                                                        queryName,
                                                                                        initialResponseType,
                                                                                        updateResponseType)))
                .flatMap(queryMessage -> {
                    SubscriptionQueryResult<QueryResponseMessage<I>, SubscriptionQueryUpdateMessage<U>> data = queryBus
                            .subscriptionQuery((SubscriptionQueryMessage<Q, I, U>) queryMessage,
                                               backpressure,
                                               updateBufferSize);
                    return Mono.just(new DefaultSubscriptionQueryResult<>(
                            data.initialResult()
                                .filter(initialResult -> Objects.nonNull(initialResult.getPayload()))
                                .map(Message::getPayload)
                                .onErrorMap(e -> e instanceof IllegalPayloadAccessException ? e.getCause() : e),
                            data.updates()
                                .filter(update -> Objects.nonNull(update.getPayload()))
                                .map(SubscriptionQueryUpdateMessage::getPayload),
                            data
                    ));
                });
    }
}

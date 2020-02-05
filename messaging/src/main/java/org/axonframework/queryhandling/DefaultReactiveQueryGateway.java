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

import org.axonframework.messaging.responsetypes.ResponseType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * Default implementation of {@link ReactiveQueryGateway}.
 *
 * @author Milan Savic
 * @since 4.4
 */
public class DefaultReactiveQueryGateway implements ReactiveQueryGateway {

    private final QueryGateway delegate;

    /**
     * Creates an instance of {@link DefaultReactiveQueryGateway}.
     *
     * @param delegate the delegate {@link QueryGateway} used to perform the actual query dispatching
     */
    public DefaultReactiveQueryGateway(QueryGateway delegate) {
        this.delegate = delegate;
    }

    @Override
    public <R, Q> Mono<R> query(String queryName, Q query, ResponseType<R> responseType) {
        return Mono.fromFuture(delegate.query(queryName, query, responseType));
    }

    @Override
    public <R, Q> Flux<R> scatterGather(String queryName, Q query, ResponseType<R> responseType, long timeout,
                                        TimeUnit timeUnit) {
        return Flux.fromStream(delegate.scatterGather(queryName, query, responseType, timeout, timeUnit));
    }

    @Override
    public <Q, I, U> SubscriptionQueryResult<I, U> subscriptionQuery(String queryName, Q query,
                                                                     ResponseType<I> initialResponseType,
                                                                     ResponseType<U> updateResponseType,
                                                                     SubscriptionQueryBackpressure backpressure,
                                                                     int updateBufferSize) {
        return delegate.subscriptionQuery(queryName,
                                          query,
                                          initialResponseType,
                                          updateResponseType,
                                          backpressure,
                                          updateBufferSize);
    }
}

/*
 * Copyright (c) 2010-2017. Axon Framework
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

import org.axonframework.messaging.MessageDispatchInterceptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * @since 3.1
 * @author Marc Gathier
 */
public class DefaultQueryGateway implements QueryGateway {
    private final QueryBus queryBus;
    private final MessageDispatchInterceptor<? super QueryMessage<?>>[] dispatchInterceptors;

    public DefaultQueryGateway(QueryBus queryBus, MessageDispatchInterceptor<? super QueryMessage<?>>... dispatchInterceptors) {
        this.queryBus = queryBus;
        this.dispatchInterceptors = dispatchInterceptors;
    }

    @Override
    public <R, Q> CompletableFuture<R> send(Q query, String resultName) {
        return send( query, query.getClass().getName(), resultName);
    }


    @Override
    public <R, Q> CompletableFuture<R> send(Q query, Class<R> resultClass) {
        return send(query, query.getClass().getName(), resultClass.getName());
    }

    @Override
    public <R, Q> CompletableFuture<R> send(Q query, String queryName, String resultName) {
        return queryBus.query(processInterceptors(new GenericQueryMessage<Q>(query, queryName, resultName)));
    }

    @Override
    public <R, Q> Stream<R> send(Q query, String resultName, long timeout, TimeUnit timeUnit) {
        return send(query, query.getClass().getName(), resultName, timeout, timeUnit);
    }

    @Override
    public <R, Q> Stream<R> send(Q query, Class<R> resultClass, long timeout, TimeUnit timeUnit) {
        return send( query, query.getClass().getName(), resultClass.getName(), timeout, timeUnit);
    }

    @Override
    public <R, Q> Stream<R> send(Q query, String queryName, String resultName, long timeout, TimeUnit timeUnit) {
        return queryBus.queryAll(processInterceptors(new GenericQueryMessage<Q>(query, queryName, resultName)), timeout, timeUnit);
    }

    protected <C> QueryMessage<? extends C> processInterceptors(QueryMessage<C> commandMessage) {
        QueryMessage<? extends C> message = commandMessage;
        for (MessageDispatchInterceptor<? super QueryMessage<?>> dispatchInterceptor : dispatchInterceptors) {
            message = (QueryMessage) dispatchInterceptor.handle(message);
        }
        return message;
    }

}

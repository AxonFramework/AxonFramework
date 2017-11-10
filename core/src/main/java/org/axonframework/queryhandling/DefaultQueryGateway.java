package org.axonframework.queryhandling;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Author: marc
 */
public class DefaultQueryGateway implements QueryGateway {
    private final QueryBus queryBus;

    public DefaultQueryGateway(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @Override
    public <R, Q> CompletableFuture<R> send(Q query, String resultName) {
        return queryBus.query(new GenericQueryMessage<Q>(query, resultName));
    }

    @Override
    public <R, Q> CompletableFuture<R> send(Q query, String queryName, String resultName) {
        return queryBus.query(new GenericQueryMessage<Q>(query, queryName, resultName));
    }

    @Override
    public <R, Q> CompletableFuture<R> send(Q query, Class<R> resultClass) {
        return send(query,resultClass.getName());
    }

    @Override
    public <R, Q> Stream<R> send(Q query, String resultName, long timeout, TimeUnit timeUnit) {
        return queryBus.queryAll(new GenericQueryMessage<Q>(query, resultName), timeout, timeUnit);
    }

    @Override
    public <R, Q> Stream<R> send(Q query, String queryName, String resultName, long timeout, TimeUnit timeUnit) {
        return queryBus.queryAll(new GenericQueryMessage<Q>(query, queryName, resultName), timeout, timeUnit);
    }

    @Override
    public <R, Q> Stream<R> send(Q query, Class<R> resultClass, long timeout, TimeUnit timeUnit) {
        return send( query, resultClass.getName(), timeout, timeUnit);
    }
}

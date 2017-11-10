package org.axonframework.queryhandling;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * @since 3.1
 * @author Marc Gathier
 */
public interface QueryGateway {
    <R,Q> CompletableFuture<R> send(Q query, String resultName);
    <R,Q> CompletableFuture<R> send(Q query, String queryName, String resultName);

    <R,Q> CompletableFuture<R> send(Q query, Class<R> resultClass);

    <R,Q> Stream<R> send(Q query, String resultName, long timeout, TimeUnit timeUnit);
    <R,Q> Stream<R> send(Q query, String queryName, String resultName, long timeout, TimeUnit timeUnit);

    <R,Q> Stream<R> send(Q query, Class<R> resultClass, long timeout, TimeUnit timeUnit);
}

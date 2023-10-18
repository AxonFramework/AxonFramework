package org.axonframework.command.messaging.reactive;

import java.util.concurrent.CompletableFuture;

public interface ReactiveCommandGateway {

    <P> CompletableFuture<Void> dispatch(P command);

    <P, R> CompletableFuture<R> dispatchWithResult(P command);
}

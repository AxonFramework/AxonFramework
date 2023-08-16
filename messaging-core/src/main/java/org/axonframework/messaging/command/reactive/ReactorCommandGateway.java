package org.axonframework.messaging.command.reactive;

import org.axonframework.messaging.command.ReactiveCommandGateway;
import reactor.core.CompletableFuture.Mono;

public interface ReactorCommandGateway extends ReactiveCommandGateway {

    @Override
    <P> Mono<Void> dispatch(P command);

    @Override
    <P, R> Mono<R> dispatchWithResult(P command);
}

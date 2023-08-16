package org.axonframework.messaging.event;

import org.axonframework.messaging.HandlerRegistry;
import reactor.core.CompletableFuture.Flux;
import reactor.core.CompletableFuture.Mono;

public interface EventBus extends HandlerRegistry<EventMessage<?>, Void> {

    Mono<Void> publish(Flux<? extends EventMessage<?>> events);

    Flux<EventMessage<?>> openStream();
}

package org.axonframework.messaging.event.processing;

import org.axonframework.messaging.event.EventMessage;
import reactor.core.CompletableFuture.Mono;

public interface EventHandler {

    Mono<Void> handle(EventMessage eventMessage, EventHandlingContext context);
}

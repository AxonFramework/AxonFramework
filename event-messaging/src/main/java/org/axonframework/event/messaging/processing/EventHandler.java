package org.axonframework.event.messaging.processing;

import org.axonframework.event.messaging.EventMessage;
import org.axonframework.messaging.MessageHandler;

import java.util.concurrent.CompletableFuture;

public interface EventHandler extends MessageHandler<EventMessage, Void> {

    CompletableFuture<Void> handle(EventMessage eventMessage, EventHandlingContext context);
}

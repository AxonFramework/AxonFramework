package org.axonframework.event.messaging;

import org.axonframework.event.messaging.processing.EventHandler;
import org.axonframework.messaging.MessageHandlerRegistry;
import org.axonframework.messaging.MessageStream;

import java.util.concurrent.CompletableFuture;

public interface EventBus extends MessageHandlerRegistry<EventMessage, Void, EventHandler> {

    CompletableFuture<Void> publish(EventMessage... events);

    MessageStream<EventMessage> openStream();
}

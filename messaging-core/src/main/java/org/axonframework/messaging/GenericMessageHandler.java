package org.axonframework.messaging;

import java.util.concurrent.CompletableFuture;

public class GenericMessageHandler<M extends Message, R> implements MessageHandler<M, R> {

    @Override
    public R handle(MessageHandlingContext<M> context) {
        return null;
    }

    public CompletableFuture<MessageHandlingContext<M>> upcast(MessageHandlingContext<M> context) {
        return new CompletableFuture();
    }

    public CompletableFuture<MessageHandlingContext<M>> intercept(MessageHandlingContext<M> context) {
        return new CompletableFuture();
    }

    public CompletableFuture<Void> handle(Message message) {
        return new CompletableFuture();
    }
}

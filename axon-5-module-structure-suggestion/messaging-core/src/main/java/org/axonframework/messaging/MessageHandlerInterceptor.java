package org.axonframework.messaging;

@FunctionalInterface
public interface MessageHandlerInterceptor<M extends Message, R> {

    MessageHandler<M, R> intercept(MessageHandler<M, R> handler);
}

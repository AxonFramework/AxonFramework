package org.axonframework.messaging;

@FunctionalInterface
public interface MessageHandler<M extends Message, R> {

    default String name() {
        return getClass().getName();
    }

    R handle(MessageHandlingContext<M> context);
}

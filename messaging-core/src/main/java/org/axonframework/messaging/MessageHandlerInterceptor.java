package org.axonframework.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface MessageHandlerInterceptor<M extends Message<?>, R> {

    Function<MessageHandlingContext<M>, CompletableFuture<R>> intercept(Function<MessageHandlingContext<M>, CompletableFuture<R>> handler);

}

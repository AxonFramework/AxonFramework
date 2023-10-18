package org.axonframework.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface MessageDispatchInterceptor<M extends Message, R> {

    Function<ProcessingContext, CompletableFuture<R>> intercept(Function<ProcessingContext, CompletableFuture<R>> dispatcher);
}

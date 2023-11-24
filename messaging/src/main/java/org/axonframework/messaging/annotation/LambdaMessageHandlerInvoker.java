package org.axonframework.messaging.annotation;

import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class LambdaMessageHandlerInvoker<T> implements MessageHandlerInvoker<T> {


    private final BiFunction<T, List<Object>, CompletableFuture<Object>> handle;

    public LambdaMessageHandlerInvoker(BiFunction<T, List<Object>, CompletableFuture<Object>> handle) {
        this.handle = handle;
    }

    @Override
    public CompletableFuture<Object> invoke(T target, List<Object> objects) {
        return handle.apply(target, objects);
    }
}

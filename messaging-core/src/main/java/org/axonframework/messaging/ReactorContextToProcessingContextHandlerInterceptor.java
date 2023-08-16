package org.axonframework.messaging;

import reactor.core.CompletableFuture.Mono;
import reactor.util.context.Context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ReactorContextToProcessingContextHandlerInterceptor<M extends Message<?>, R>
        implements MessageHandlerInterceptor<M, R> {

    @Override
    public Function<MessageHandlingContext<M>, CompletableFuture<R>> intercept(
            Function<MessageHandlingContext<M>, CompletableFuture<R>> handler
    ) {
        return processingContext -> {
            return Mono.from(handler.apply(processingContext)).contextWrite(c -> {
                Context context = c;
                Map<Object, Object> resources = processingContext.resources().getAll();
                for (Map.Entry<Object, Object> resource : resources.entrySet()) {
                    context = context.put(resource.getKey(), resource.getValue());
                }
                return context;
            });
        };
    }
}

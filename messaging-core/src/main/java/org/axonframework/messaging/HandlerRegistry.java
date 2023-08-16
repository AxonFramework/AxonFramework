package org.axonframework.messaging;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface HandlerRegistry<M extends Message<?>, R> {

    Registration registerComponent(String componentName,
                                   Set<QualifiedName> messageTypes,
                                   Function<MessageHandlingContext<M>, CompletableFuture<R>> component);
}

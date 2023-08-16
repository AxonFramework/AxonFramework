package org.axonframework.messaging.command;

import org.axonframework.messaging.HandlerRegistry;
import org.axonframework.messaging.MessageHandlingContext;
import reactor.util.annotation.Nullable;

import java.util.concurrent.CompletableFuture;

public interface CommandBus extends HandlerRegistry<CommandMessage<?>, CommandResultMessage<?>> {

    CompletableFuture<Void> dispatch(CommandMessage<?> command,
                             DispatchProperties dispatchProperties,
                             @Nullable MessageHandlingContext<?> processingContext);

    CompletableFuture<CommandResultMessage<?>> resultStream();
}

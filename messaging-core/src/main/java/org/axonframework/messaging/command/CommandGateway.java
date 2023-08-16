package org.axonframework.messaging.command;

import java.util.concurrent.CompletableFuture;

public interface CommandGateway {

    CompletableFuture<Void> dispatch(Object command);

    CompletableFuture<Void> dispatch(CommandMessage<?> commandMessage);

    <R> CompletableFuture<R> dispatchWithResult(Object command, Class<R> resultType);

    <R> CompletableFuture<CommandResultMessage<R>> dispatchWithResult(CommandMessage<?> command, Class<R> resultType);
}

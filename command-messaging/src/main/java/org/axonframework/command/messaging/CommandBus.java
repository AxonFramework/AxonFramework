package org.axonframework.command.messaging;

import org.axonframework.messaging.MessageHandlerRegistry;
import org.axonframework.messaging.MessageHandlingContext;
import reactor.util.annotation.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 *
 */
public interface CommandBus extends MessageHandlerRegistry<CommandMessage, CommandResultMessage, CommandHandler> {



    // 1. Fire and Forget - just send, no handling response, just dispatching! So no Identifiers too, or error codes / exceptions. Just dispatch
    CompletableFuture<Void> fireAndForget(CommandMessage commandMessage,
                                          MessageHandlingContext<?> processingContext);

    // 2. Request and Response - send and get the response eventually
    <T> CompletableFuture<T> requestResponse(CommandMessage commandMessage,
                                             MessageHandlingContext<?> processingContext,
                                             DispatchProperties dispatchProperties);

    default CompletableFuture<Void> dispatch(CommandMessage command,
                                             @Nullable MessageHandlingContext<?> processingContext) {
        return dispatch(command, processingContext, new DispatchProperties.FireAndForget());
    }

    /**
     * @return Result of DISPATCHING, of RECEIVING, or of HANDLING?
     * IT DEPENDS
     */
    CompletableFuture<Void> dispatch(CommandMessage command,
                                     @Nullable MessageHandlingContext<?> processingContext,
                                     DispatchProperties dispatchProperties);

    CompletableFuture<CommandResultMessage> resultStream();

    /**
     * TODO next sessions
     *
     * Investigate the construction of a generic MessageBus that provides us the basis messaging patterns.
     *
     * This MessageBus is to be used by the CommandBus, EventBus, and QueryBus, which in turn restrict the patterns to
     * their preferred formats. In doing so, we guide uses to use commands/events/queries as intended,
     * while still providing the flexibility for users that don't given an arse.
     */

    // Sara's little space


//    public static void main(String[] args) {
//        CommandBus commandBus = null;
//
//        // async-thought
//        //eventBus.subscribe(toEvent, () -> command done);
//        commandBus.dispatch("MyCommand");
//
//        // sync-thought
//        CompletableFuture<String> reply = commandBus.dispatch("command", String.class);
//    }
//
//    default void aCommandHandler(Object myCommand){
//
//    }
//
//    default String anotherCommandHandler(Object myCommand){
//        return "";
//    }

}



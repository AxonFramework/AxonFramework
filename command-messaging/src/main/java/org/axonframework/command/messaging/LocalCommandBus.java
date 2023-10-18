package org.axonframework.command.messaging;

import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlingContext;
import org.axonframework.messaging.ProcessingContext;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.Registration;
import org.axonframework.messaging.Resources;
import org.axonframework.messaging.UnitOfWork;
import reactor.util.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class LocalCommandBus implements CommandBus {

    private final Executor scheduler;

    private final ConcurrentHashMap<QualifiedName, Function<MessageHandlingContext<CommandMessage>, CompletableFuture<CommandResultMessage>>> handlers = new ConcurrentHashMap<>();

    public LocalCommandBus(Executor scheduler) {
        this.scheduler = scheduler;
    }

    public LocalCommandBus(/*add interceptors*/) {
        this(Runnable::run);
    }

    //    @Override
    public Registration registerComponent(String componentName,
                                          Set<QualifiedName> qualifiedHandlers,
                                          Function<MessageHandlingContext<CommandMessage>, CompletableFuture<CommandResultMessage>> component) {
        // TODO fix this bit to hold components instead of handlers
        handlers.put(qualifiedHandlers.iterator().next(), component);
        return () -> {
        };
    }

    //    @Override
    public CompletableFuture<Void> dispatch(CommandMessage command,
                                            DispatchProperties dispatchProperties,
                                            @Nullable MessageHandlingContext processingContext) {
        Function<MessageHandlingContext<CommandMessage>, CompletableFuture<CommandResultMessage>> handler =
                handlers.get(command.name());
        if (handler == null) {
            throw new IllegalArgumentException("No handler for command");
        }

        MessageHandlingContext<CommandMessage> context = (MessageHandlingContext<CommandMessage>) processingContext;
        if (context == null) {
            context = new MessageHandlingContext<>() {
                // NoOp context
                @Override
                public CommandMessage message() {
                    return null;
                }

                @Override
                public ProcessingContext processingContext() {
                    return null;
                }

                @Override
                public Resources resources() {
                    return null;
                }
            };
        }

        UnitOfWork unitOfWork = new UnitOfWork(command.identifier(), parentContext(processingContext), scheduler);
//        CompletableFuture<CommandResultMessage<R>> commandResultMessageCompletableFuture = unitOfWork.streamingResult(pc -> handler.apply(null));
        CompletableFuture<CommandResultMessage> result = handler.apply(context);
        return null;
    }

    private ProcessingContext parentContext(MessageHandlingContext processingContext) {
        return processingContext == null ? null : processingContext.processingContext();
    }

    @Override
    public CompletableFuture<Void> fireAndForget(CommandMessage commandMessage,
                                                 MessageHandlingContext<?> processingContext) {
        return null;
    }

    @Override
    public <T> CompletableFuture<T> requestResponse(CommandMessage commandMessage,
                                                    MessageHandlingContext<?> processingContext,
                                                    DispatchProperties dispatchProperties) {
        return null;
    }

    @Override
    public CompletableFuture<Void> dispatch(CommandMessage command, MessageHandlingContext<?> processingContext,
                                            DispatchProperties dispatchProperties) {
        return null;
    }

    @Override
    public CompletableFuture<CommandResultMessage> resultStream() {
        return null;
    }

    @Override
    public Registration registerHandler(Set<QualifiedName> messageTypes, CommandHandler handler) {
        return null;
    }

    @Override
    public Registration registerInterceptor(
            MessageHandlerInterceptor<CommandMessage, CommandResultMessage> interceptor) {
        return null;
    }
}

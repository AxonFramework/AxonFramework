/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.interceptors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.messaging.DefaultMessageDispatchInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * A {@code CommandBus} wrapper that supports both {@link MessageHandlerInterceptor MessageHandlerInterceptors} and
 * {@link MessageDispatchInterceptor MessageDispatchInterceptors}. Actual dispatching and handling of commands is done
 * by a delegate.
 * <p>
 * This {@code InterceptingCommandBus} is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and automatically kicks in whenever
 * {@link CommandMessage} specific {@code MessageHandlerInterceptors} or any {@code MessageDispatchInterceptors} are
 * present.
 *
 * @author Allad Buijze
 * @author Simon Zambrovski
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class InterceptingCommandBus implements CommandBus {

    /**
     * The order in which the {@link InterceptingCommandBus} is applied as a
     * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the {@link CommandBus}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code InterceptingCommandBus} itself. Using the same value can either lead to application of the
     * decorator to the delegate or the {@code InterceptingCommandBus}, depending on the order of registration.
     * <p>
     * The order of the {@code InterceptingCommandBus} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final CommandBus delegate;
    private final List<MessageHandlerInterceptor<CommandMessage>> handlerInterceptors;
    private final List<MessageDispatchInterceptor<? super CommandMessage>> dispatchInterceptors;
    private final InterceptingDispatcher interceptingDispatcher;

    /**
     * Constructs a {@code InterceptingCommandBus}, delegating dispatching and handling logic to the given
     * {@code delegate}. The given {@code handlerInterceptors} are wrapped around the
     * {@link CommandHandler command handlers} when subscribing. The given {@code dispatchInterceptors} are invoked
     * before dispatching is provided to the given {@code delegate}.
     *
     * @param delegate             The delegate {@code CommandBus} that will handle all dispatching and handling logic.
     * @param handlerInterceptors  The interceptors to invoke before handling a command and if present on the command
     *                             result.
     * @param dispatchInterceptors The interceptors to invoke before dispatching a command and on the command result.
     */
    public InterceptingCommandBus(
            @Nonnull CommandBus delegate,
            @Nonnull List<MessageHandlerInterceptor<CommandMessage>> handlerInterceptors,
            @Nonnull List<MessageDispatchInterceptor<? super CommandMessage>> dispatchInterceptors
    ) {
        this.delegate = requireNonNull(delegate, "The command bus delegate must be null.");
        this.handlerInterceptors = new ArrayList<>(
                requireNonNull(handlerInterceptors, "The handler interceptors must not be null.")
        );
        this.dispatchInterceptors = new ArrayList<>(
                requireNonNull(dispatchInterceptors, "The dispatch interceptors must not be null.")
        );
        this.interceptingDispatcher = new InterceptingDispatcher(dispatchInterceptors, this::dispatchCommand);
    }

    @Override
    public InterceptingCommandBus subscribe(@Nonnull QualifiedName name,
                                            @Nonnull CommandHandler commandHandler) {
        delegate.subscribe(name, new InterceptingHandler(commandHandler, handlerInterceptors));
        return this;
    }

    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        return interceptingDispatcher.interceptAndDispatch(command, processingContext);
    }

    private MessageStream<?> dispatchCommand(@Nonnull Message message,
                                             @Nullable ProcessingContext processingContext) {
        if (!(message instanceof CommandMessage command)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported message implementation: " + message);
        }
        return MessageStream.fromFuture(delegate.dispatch(command, processingContext));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("handlerInterceptors", handlerInterceptors);
        descriptor.describeProperty("dispatchInterceptors", dispatchInterceptors);
    }

    private static class InterceptingHandler implements CommandHandler {

        private final CommandMessageHandlerInterceptorChain interceptorChain;

        private InterceptingHandler(CommandHandler handler,
                                    List<MessageHandlerInterceptor<CommandMessage>> interceptors) {
            this.interceptorChain = new CommandMessageHandlerInterceptorChain(interceptors, handler);
        }

        @Nonnull
        @Override
        public MessageStream.Single<CommandResultMessage> handle(@Nonnull CommandMessage command,
                                                                 @Nonnull ProcessingContext context) {
            return interceptorChain.proceed(command, context)
                                   .first()
                                   .cast();
        }
    }

    private static class InterceptingDispatcher {

        private final DefaultMessageDispatchInterceptorChain<? super CommandMessage> interceptorChain;

        private InterceptingDispatcher(
                List<MessageDispatchInterceptor<? super CommandMessage>> interceptors,
                BiFunction<? super CommandMessage, ProcessingContext, MessageStream<?>> dispatcher
        ) {
            this.interceptorChain = new DefaultMessageDispatchInterceptorChain<>(interceptors, dispatcher);
        }

        private CompletableFuture<CommandResultMessage> interceptAndDispatch(
                @Nonnull CommandMessage command,
                @Nullable ProcessingContext context
        ) {
            return interceptorChain.proceed(command, context)
                                   .first()
                                   .<CommandResultMessage>cast()
                                   .asCompletableFuture()
                                   .thenApply(Entry::message);
        }
    }
}

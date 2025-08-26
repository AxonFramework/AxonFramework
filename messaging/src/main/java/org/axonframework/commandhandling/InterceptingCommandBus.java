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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.CommandMessageHandlerInterceptorChain;
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

import static java.util.Objects.requireNonNull;

/**
 * A {@code CommandBus} wrapper that supports both {@link MessageHandlerInterceptor MessageHandlerInterceptors} and
 * {@link MessageDispatchInterceptor MessageDispatchInterceptors}. Actual dispatching and handling of commands is done
 * by a delegate.
 *
 * @author Allad Buijze
 * @author Simon Zambrovski
 * @since 5.0.0
 */
public class InterceptingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final List<MessageHandlerInterceptor<CommandMessage>> handlerInterceptors;
    private final List<MessageDispatchInterceptor<? super Message>> dispatchInterceptors;


    /**
     * Constructs a {@code InterceptingCommandBus}, delegating dispatching and handling logic to the given
     * {@code delegate}. The given {@code handlerInterceptors} are wrapped around the
     * {@link CommandHandler command handlers} when subscribing. The given {@code dispatchInterceptors} are invoked
     * before dispatching is provided to the given {@code delegate}.
     *
     * @param delegate             The delegate {@code CommandBus} that will handle all dispatching and handling logic.
     * @param handlerInterceptors  The interceptors to invoke before handling a command and if present on the command result.
     * @param dispatchInterceptors The interceptors to invoke before dispatching a command and on the command result.
     */
    public InterceptingCommandBus(
            @Nonnull CommandBus delegate,
            @Nonnull List<MessageHandlerInterceptor<CommandMessage>> handlerInterceptors,
            @Nonnull List<MessageDispatchInterceptor<? super Message>> dispatchInterceptors
    ) {
        this.delegate = requireNonNull(delegate, "The command bus delegate must be null.");
        this.handlerInterceptors = new ArrayList<>(
                requireNonNull(handlerInterceptors, "The handler interceptors must not be null.")
        );
        this.dispatchInterceptors = new ArrayList<>(
                requireNonNull(dispatchInterceptors, "The dispatch interceptors must not be null.")
        );
    }

    @Override
    public InterceptingCommandBus subscribe(@Nonnull QualifiedName name,
                                            @Nonnull CommandHandler commandHandler) {
        CommandHandler handler = requireNonNull(commandHandler, "The command handler cannot be null.");
        delegate.subscribe(name, (command, context)
                -> new CommandMessageHandlerInterceptorChain(handlerInterceptors, handler)
                .proceed(command, context)
                .first()
                .cast());
        return this;
    }

    @Override
    public CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage command,
                                                               @Nullable ProcessingContext processingContext) {
        return new DefaultMessageDispatchInterceptorChain<>(dispatchInterceptors, this::dispatchMessage)
                .proceed(requireNonNull(command, "The command message cannot be null."), processingContext)
                .first()
                .<CommandResultMessage<?>>cast()
                .asCompletableFuture()
                .thenApply(Entry::message);
    }

    MessageStream<?> dispatchMessage(@Nonnull Message message, @Nullable ProcessingContext processingContext) {
        return MessageStream.fromFuture(delegate.dispatch((CommandMessage) message, processingContext));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("handlerInterceptors", handlerInterceptors);
        descriptor.describeProperty("dispatchInterceptors", dispatchInterceptors);
    }
}

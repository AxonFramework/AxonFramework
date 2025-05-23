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
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStream.Entry;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * A {@code CommandBus} wrapper that supports both {@link MessageHandlerInterceptor MessageHandlerInterceptors} and
 * {@link MessageDispatchInterceptor MessageDispatchInterceptors}. Actual dispatching and handling of commands is done
 * by a delegate.
 *
 * @author Allad Buijze
 * @since 5.0.0
 */
public class InterceptingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final LinkedList<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors;
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors;
    private final BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<CommandResultMessage<?>>> dispatcher;

    /**
     * Constructs a {@code InterceptingCommandBus}, delegating dispatching and handling logic to the given
     * {@code delegate}. The given {@code handlerInterceptors} are wrapped around the
     * {@link CommandHandler command handlers} when subscribing. The given {@code dispatchInterceptors} are invoked
     * before dispatching is provided to the given {@code delegate}.
     *
     * @param delegate             The delegate {@code CommandBus} that will handle all dispatching and handling logic.
     * @param handlerInterceptors  The interceptors to invoke before handling a command.
     * @param dispatchInterceptors The interceptors to invoke before dispatching a command.
     */
    public InterceptingCommandBus(
            @Nonnull CommandBus delegate,
            @Nonnull List<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors,
            @Nonnull List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors
    ) {
        this.delegate = requireNonNull(delegate, "The command bus delegate must be null.");
        this.handlerInterceptors = new LinkedList<>(
                requireNonNull(handlerInterceptors, "The handler interceptors must not be null.")
        );
        this.dispatchInterceptors = new ArrayList<>(
                requireNonNull(dispatchInterceptors, "The dispatch interceptors must not be null.")
        );

        Iterator<MessageDispatchInterceptor<? super CommandMessage<?>>> di =
                new LinkedList<>(dispatchInterceptors).descendingIterator();
        BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<CommandResultMessage<?>>> dis =
                (c, p) -> MessageStream.fromFuture(delegate.dispatch(c, p));
        while (di.hasNext()) {
            dis = new Dispatcher(di.next(), dis);
        }
        this.dispatcher = dis;
    }

    @Override
    public InterceptingCommandBus subscribe(@Nonnull QualifiedName name,
                                            @Nonnull CommandHandler commandHandler) {
        CommandHandler handler = requireNonNull(commandHandler, "The command handler cannot be null.");
        Iterator<MessageHandlerInterceptor<? super CommandMessage<?>>> iter = handlerInterceptors.descendingIterator();
        CommandHandler interceptedHandler = handler;
        while (iter.hasNext()) {
            interceptedHandler = new InterceptedHandler(iter.next(), interceptedHandler);
        }
        delegate.subscribe(name, interceptedHandler);
        return this;
    }

    @Override
    public CompletableFuture<CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                               @Nullable ProcessingContext processingContext) {
        return dispatcher.apply(requireNonNull(command, "The command message cannot be null."), processingContext)
                         .first()
                         .asCompletableFuture()
                         .thenApply(Entry::message);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("handlerInterceptors", handlerInterceptors);
        descriptor.describeProperty("dispatchInterceptors", dispatchInterceptors);
    }

    private record InterceptedHandler(
            MessageHandlerInterceptor<? super CommandMessage<?>> interceptor,
            CommandHandler next
    ) implements CommandHandler, InterceptorChain<CommandMessage<?>, CommandResultMessage<?>> {

        @Nonnull
        @Override
        public MessageStream.Single<CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> message,
                                                                    @Nonnull ProcessingContext processingContext) {
            try {
                return interceptor.interceptOnHandle(message, processingContext, this)
                                  .first();
            } catch (RuntimeException e) {
                return MessageStream.failed(e);
            }
        }

        @Override
        public Object proceedSync(@Nonnull ProcessingContext context) {
            throw new UnsupportedOperationException("Sync processing not supported");
        }

        @Override
        public MessageStream<CommandResultMessage<?>> proceed(@Nonnull CommandMessage<?> message,
                                                              @Nonnull ProcessingContext context) {
            return next.handle(message, context);
        }
    }

    private record Dispatcher(
            MessageDispatchInterceptor<? super CommandMessage<?>> interceptor,
            BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<CommandResultMessage<?>>> next
    ) implements BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<CommandResultMessage<?>>>,
            InterceptorChain<CommandMessage<?>, CommandResultMessage<?>> {

        @Override
        public MessageStream<CommandResultMessage<?>> apply(CommandMessage<?> commandMessage,
                                                            ProcessingContext processingContext) {
            try {
                return interceptor.interceptOnDispatch(commandMessage, processingContext, this);
            } catch (RuntimeException e) {
                return MessageStream.failed(e);
            }
        }

        @Override
        public Object proceedSync(@Nonnull ProcessingContext context) {
            throw new UnsupportedOperationException("Sync processing not supported ");
        }

        @Override
        public MessageStream<CommandResultMessage<?>> proceed(@Nonnull CommandMessage<?> message,
                                                              @Nonnull ProcessingContext context) {
            return next.apply(message, context);
        }
    }
}

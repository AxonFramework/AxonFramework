/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class InterceptingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final LinkedList<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors;
    private final BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<? extends CommandResultMessage<?>>> dispatcher;
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors;

    public InterceptingCommandBus(CommandBus delegate,
                                  List<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors,
                                  List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors) {
        this.delegate = delegate;
        this.handlerInterceptors = new LinkedList<>(handlerInterceptors);
        this.dispatchInterceptors = new ArrayList<>(dispatchInterceptors);
        Iterator<MessageDispatchInterceptor<? super CommandMessage<?>>> di = new LinkedList<>(dispatchInterceptors).descendingIterator();
        BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<? extends CommandResultMessage<?>>> dis = (c, p) -> MessageStream.fromFuture(
                delegate.dispatch(c, p));
        while (di.hasNext()) {
            dis = new Dispatcher(di.next(), dis);
        }
        this.dispatcher = dis;
    }

    @Override
    public CompletableFuture<? extends CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                                         @Nullable ProcessingContext processingContext) {
        return dispatcher.apply(command, processingContext).asCompletableFuture();
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
        Iterator<MessageHandlerInterceptor<? super CommandMessage<?>>> iter = handlerInterceptors.descendingIterator();
        MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> interceptedHandler = handler;
        while (iter.hasNext()) {
            interceptedHandler = new InterceptedHandler(iter.next(), interceptedHandler);
        }
        return delegate.subscribe(commandName, interceptedHandler);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("handlerInterceptors", handlerInterceptors);
        descriptor.describeProperty("dispatchInterceptors", dispatchInterceptors);
    }

    private static class InterceptedHandler implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>>,
            InterceptorChain<CommandMessage<?>, CommandResultMessage<?>> {

        private final MessageHandlerInterceptor<? super CommandMessage<?>> interceptor;
        private final MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> next;

        public InterceptedHandler(MessageHandlerInterceptor<? super CommandMessage<?>> interceptor,
                                  MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> next) {
            this.interceptor = interceptor;
            this.next = next;
        }

        @Override
        public Object handleSync(CommandMessage<?> message) throws Exception {
            return next.handleSync(message);
        }

        @Override
        public MessageStream<? extends CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                       ProcessingContext processingContext) {
            try {
                return interceptor.interceptOnHandle(message, processingContext, this);
            } catch (RuntimeException e) {
                return MessageStream.failed(e);
            }
        }

        @Override
        public Object proceedSync() {
            throw new UnsupportedOperationException("Sync processing not supported");
        }

        @Override
        public MessageStream<? extends CommandResultMessage<?>> proceed(CommandMessage<?> message,
                                                                        ProcessingContext processingContext) {
            return next.handle(message, processingContext);
        }
    }

    private class Dispatcher implements
            BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<? extends CommandResultMessage<?>>>,
            InterceptorChain<CommandMessage<?>, CommandResultMessage<?>> {

        private final MessageDispatchInterceptor<? super CommandMessage<?>> interceptor;
        private final BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<? extends CommandResultMessage<?>>> next;

        public Dispatcher(MessageDispatchInterceptor<? super CommandMessage<?>> interceptor,
                          BiFunction<CommandMessage<?>, ProcessingContext, MessageStream<? extends CommandResultMessage<?>>> next) {

            this.interceptor = interceptor;
            this.next = next;
        }

        @Override
        public MessageStream<? extends CommandResultMessage<?>> apply(CommandMessage<?> commandMessage,
                                                                      ProcessingContext processingContext) {
            try {
                return interceptor.interceptOnDispatch(commandMessage, processingContext, this);
            } catch (RuntimeException e) {
                return MessageStream.failed(e);
            }
        }

        @Override
        public Object proceedSync() {
            throw new UnsupportedOperationException("Sync processing not supported ");
        }

        @Override
        public MessageStream<? extends CommandResultMessage<?>> proceed(CommandMessage<?> message,
                                                                        ProcessingContext processingContext) {
            return next.apply(message, processingContext);
        }
    }
}

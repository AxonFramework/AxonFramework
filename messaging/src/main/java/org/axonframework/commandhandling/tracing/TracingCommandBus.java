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

package org.axonframework.commandhandling.tracing;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.Span;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A CommandBus wrapper that adds tracing for outgoing and incoming commands. It creates a span for Dispatching the
 * command as well as a separate span for handling it.
 */
public class TracingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final CommandBusSpanFactory spanFactory;

    /**
     * Initialize the TracingCommandBus to wrap the given {@code delegate} by recording traces on the given
     * {@code spanFactory}
     *
     * @param delegate    The Command Bus to delegate calls to
     * @param spanFactory The Span Factory to create spans with
     */
    public TracingCommandBus(CommandBus delegate, CommandBusSpanFactory spanFactory) {
        this.delegate = delegate;
        this.spanFactory = spanFactory;
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                            @Nullable ProcessingContext processingContext) {
        Span span = spanFactory.createDispatchCommandSpan(command, false);
        return span.runSupplierAsync(() -> delegate.dispatch(spanFactory.propagateContext(command), processingContext));
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends Message<?>> handler) {
        return delegate.subscribe(commandName, new TracingHandler(handler));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("spanFactory", spanFactory);
    }

    private class TracingHandler implements MessageHandler<CommandMessage<?>, Message<?>> {

        private final MessageHandler<? super CommandMessage<?>, ? extends Message<?>> handler;

        public TracingHandler(MessageHandler<? super CommandMessage<?>, ? extends Message<?>> handler) {
            this.handler = handler;
        }

        @Override
        public MessageStream<? extends Message<?>> handle(CommandMessage<?> message,
                                                          ProcessingContext processingContext) {
            return MessageStream.fromFuture(spanFactory.createHandleCommandSpan(message, false)
                                                       .runSupplierAsync(() -> handler.handle(message,
                                                                                              processingContext)
                                                                                      .asCompletableFuture()));
        }

        @Override
        public Object handleSync(CommandMessage<?> message) throws Exception {
            return spanFactory.createHandleCommandSpan(message, false)
                              .runCallable(() -> handler.handleSync(message));
        }
    }
}

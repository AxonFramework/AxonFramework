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
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.common.Registration;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.Span;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TracingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final CommandBusSpanFactory spanFactory;

    public TracingCommandBus(CommandBus delegate, CommandBusSpanFactory spanFactory) {
        this.delegate = delegate;
        this.spanFactory = spanFactory;
    }

    @Override
    public CompletableFuture<? extends CommandResultMessage<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                                         @Nullable ProcessingContext processingContext) {
        Span span = spanFactory.createDispatchCommandSpan(command, false);
        return span.runSupplierAsync(() -> delegate.dispatch(command, processingContext));
    }

    @Override
    public Registration subscribe(@Nonnull String commandName,
                                  @Nonnull MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
        return delegate.subscribe(commandName, new TracingHandler(handler));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("spanFactory", spanFactory);
    }

    private class TracingHandler implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private final MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler;

        public TracingHandler(MessageHandler<? super CommandMessage<?>, ? extends CommandResultMessage<?>> handler) {
            this.handler = handler;
        }

        @Override
        public MessageStream<? extends CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                       ProcessingContext processingContext) {
            return spanFactory.createHandleCommandSpan(message, false)
                              .runSupplier(() -> handler.handle(message, processingContext));
        }

        @Override
        public Object handleSync(CommandMessage<?> message) throws Exception {
            return spanFactory.createHandleCommandSpan(message, delegate instanceof DistributedCommandBus)
                              .runCallable(() -> handler.handleSync(message));
        }
    }
}

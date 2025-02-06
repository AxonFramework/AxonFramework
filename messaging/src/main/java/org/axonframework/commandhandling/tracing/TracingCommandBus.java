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

package org.axonframework.commandhandling.tracing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.Span;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A {@code CommandBus} wrapper that adds tracing for outgoing and incoming {@link CommandMessage commands}.
 * <p>
 * It creates a span for dispatching the command as well as a separate span for handling it.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class TracingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final CommandBusSpanFactory spanFactory;

    /**
     * Initialize the {@code TracingCommandBus} to wrap the given {@code delegate} by recording traces on the given
     * {@code spanFactory}.
     *
     * @param delegate    The delegate {@code CommandBus} that will handle all dispatching and handling logic.
     * @param spanFactory The {@code CommandBusSpanFactory} to create spans with.
     */
    public TracingCommandBus(@Nonnull CommandBus delegate,
                             @Nonnull CommandBusSpanFactory spanFactory) {
        this.delegate = Objects.requireNonNull(delegate, "Given CommandBus delegate cannot be null.");
        this.spanFactory = Objects.requireNonNull(spanFactory, "Given CommandBusSpanFactory cannot be null.");
    }

    @Override
    public CommandBus subscribe(@Nonnull QualifiedName name,
                                @Nonnull CommandHandler handler) {
        delegate.subscribe(name, new TracingHandler(Objects.requireNonNull(handler, "Given handler cannot be null.")));
        return this;
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                            @Nullable ProcessingContext processingContext) {
        Span span = spanFactory.createDispatchCommandSpan(command, false);
        return span.runSupplierAsync(() -> delegate.dispatch(spanFactory.propagateContext(command), processingContext));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("spanFactory", spanFactory);
    }

    private class TracingHandler implements CommandHandler {

        private final CommandHandler handler;

        public TracingHandler(CommandHandler handler) {
            this.handler = handler;
        }

        @Nonnull
        @Override
        public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> message,
                                                                       @Nonnull ProcessingContext processingContext) {
            return spanFactory.createHandleCommandSpan(message, false)
                              .runSupplier(() -> handler.handle(message, processingContext));
        }
    }
}

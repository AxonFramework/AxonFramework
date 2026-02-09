/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.commandhandling.interception;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.DecoratorDefinition;
import org.axonframework.messaging.core.DefaultMessageDispatchInterceptorChain;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * A {@code CommandBus} wrapper that supports {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
 * Actual dispatching and handling of commands is done by a delegate.
 * <p>
 * Handler interceptors are applied at the component level via
 * {@link InterceptingCommandHandlingComponent} rather than at the bus level,
 * ensuring each module gets its own set of handler interceptors with the correct
 * {@link org.axonframework.messaging.core.ApplicationContext ApplicationContext}.
 * <p>
 * This {@code InterceptingCommandBus} is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and automatically kicks in whenever
 * any {@code MessageDispatchInterceptors} are present.
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
    private final List<MessageDispatchInterceptor<? super CommandMessage>> dispatchInterceptors;
    private final InterceptingDispatcher interceptingDispatcher;

    /**
     * Constructs a {@code InterceptingCommandBus}, delegating dispatching and handling logic to the given
     * {@code delegate}. The given {@code dispatchInterceptors} are invoked before dispatching is provided to the
     * given {@code delegate}.
     *
     * @param delegate             The delegate {@code CommandBus} that will handle all dispatching and handling logic.
     * @param dispatchInterceptors The interceptors to invoke before dispatching a command and on the command result.
     */
    public InterceptingCommandBus(
            @Nonnull CommandBus delegate,
            @Nonnull List<MessageDispatchInterceptor<? super CommandMessage>> dispatchInterceptors
    ) {
        this.delegate = requireNonNull(delegate, "The command bus delegate must be null.");
        this.dispatchInterceptors = new ArrayList<>(
                requireNonNull(dispatchInterceptors, "The dispatch interceptors must not be null.")
        );
        this.interceptingDispatcher = new InterceptingDispatcher(dispatchInterceptors, this::dispatchCommand);
    }

    @Override
    public InterceptingCommandBus subscribe(@Nonnull QualifiedName name,
                                            @Nonnull CommandHandler commandHandler) {
        delegate.subscribe(name, commandHandler);
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
        descriptor.describeProperty("dispatchInterceptors", dispatchInterceptors);
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
                                   .thenApply(entry -> entry == null ? null : entry.message());
        }
    }
}

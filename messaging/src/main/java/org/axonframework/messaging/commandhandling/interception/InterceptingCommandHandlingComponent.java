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
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.DecoratorDefinition;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.commandhandling.CommandHandlingComponent;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Set;

/**
 * A {@link CommandHandlingComponent} decorator that applies a list of
 * {@link MessageHandlerInterceptor MessageHandlerInterceptors} before delegating to the wrapped component.
 * <p>
 * This is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to automatically wrap all
 * {@link CommandHandlingComponent} instances with handler interceptors from the
 * {@link org.axonframework.messaging.core.interception.HandlerInterceptorRegistry HandlerInterceptorRegistry},
 * ensuring that interceptors (such as the
 * {@link org.axonframework.messaging.core.interception.ApplicationContextHandlerInterceptor
 * ApplicationContextHandlerInterceptor}) are applied at the component level. This means handlers within a module
 * resolve components from the module's
 * {@link org.axonframework.common.configuration.Configuration Configuration} rather than the root one.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 * @see CommandMessageHandlerInterceptorChain
 */
@Internal
public class InterceptingCommandHandlingComponent implements CommandHandlingComponent {

    /**
     * The order in which the {@link InterceptingCommandHandlingComponent} is applied as a
     * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to
     * {@link CommandHandlingComponent} instances.
     * <p>
     * Set to {@code Integer.MAX_VALUE - 100} to ensure handler interceptors wrap as an outer layer,
     * while still leaving room for decorators that need to wrap even further outside.
     */
    public static final int DECORATION_ORDER = Integer.MAX_VALUE - 100;

    private final CommandHandlingComponent delegate;
    private final CommandMessageHandlerInterceptorChain chain;

    /**
     * Constructs an {@link InterceptingCommandHandlingComponent} wrapping the given {@code delegate} with the provided
     * {@code interceptors}.
     *
     * @param interceptors The list of handler interceptors to apply before the delegate handles the command.
     * @param delegate     The {@link CommandHandlingComponent} to delegate to after interceptors have been applied.
     */
    public InterceptingCommandHandlingComponent(
            @Nonnull List<MessageHandlerInterceptor<? super CommandMessage>> interceptors,
            @Nonnull CommandHandlingComponent delegate
    ) {
        this.delegate = delegate;
        this.chain = new CommandMessageHandlerInterceptorChain(interceptors, delegate);
    }

    @Nonnull
    @Override
    public Set<QualifiedName> supportedCommands() {
        return delegate.supportedCommands();
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage> handle(@Nonnull CommandMessage command,
                                                              @Nonnull ProcessingContext context) {
        //noinspection unchecked | The interceptor chain wraps a CommandHandler returning Single<CommandResultMessage>
        return (MessageStream.Single<CommandResultMessage>) chain.proceed(command, context);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        delegate.describeTo(descriptor);
    }
}

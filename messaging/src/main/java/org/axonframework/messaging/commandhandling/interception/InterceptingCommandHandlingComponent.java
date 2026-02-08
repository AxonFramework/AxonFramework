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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.commandhandling.CommandHandlingComponent;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link CommandHandlingComponent} implementation that supports intercepting command handling through
 * {@link MessageHandlerInterceptor MessageHandlerInterceptors}.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class InterceptingCommandHandlingComponent implements CommandHandlingComponent {

    private final CommandHandlingComponent delegate;
    private final CommandMessageHandlerInterceptorChain interceptorChain;

    /**
     * Constructs the component with the given delegate and interceptors.
     *
     * @param interceptors The list of interceptors to initialize with.
     * @param delegate     The {@link CommandHandlingComponent} to delegate to.
     */
    public InterceptingCommandHandlingComponent(
            @Nonnull List<MessageHandlerInterceptor<? super CommandMessage>> interceptors,
            @Nonnull CommandHandlingComponent delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "The command handling component may not be null.");
        this.interceptorChain = new CommandMessageHandlerInterceptorChain(
                Objects.requireNonNull(interceptors, "The handler interceptors must not be null."),
                delegate
        );
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage> handle(@Nonnull CommandMessage command,
                                                             @Nonnull ProcessingContext context) {
        return interceptorChain.proceed(command, context)
                               .first()
                               .cast();
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return delegate.supportedCommands();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("interceptorChain", interceptorChain);
    }
}

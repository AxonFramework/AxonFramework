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

package org.axonframework.messaging.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;

/**
 * TODO Should reside in the query module
 * TODO Should have an interface.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class CommandModelComponent implements MessageHandlingComponent<Message<?>, Message<?>> {

    private final MessageHandlingComponent<Message<?>, Message<?>> delegate;

    public CommandModelComponent() {
        this.delegate = new GenericMessageHandlingComponent();
    }

    @Nonnull
    @Override
    public MessageStream<Message<?>> handle(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        return delegate.handle(message, context);
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> CommandModelComponent registerMessageHandler(
            @Nonnull Set<QualifiedName> messageTypes,
            @Nonnull H messageHandler
    ) {
        if (messageHandler instanceof QueryHandler) {
            throw new UnsupportedOperationException("Cannot register command handlers on a command model component");
        }
        delegate.registerMessageHandler(messageTypes, messageHandler);
        return this;
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> CommandModelComponent registerMessageHandler(
            @Nonnull QualifiedName messageType,
            @Nonnull H messageHandler
    ) {
        return registerMessageHandler(Set.of(messageType), messageHandler);
    }

    public <C extends CommandHandler> CommandModelComponent registerCommandHandler(@Nonnull QualifiedName messageType,
                                                                                   @Nonnull C commandHandler) {
        return registerMessageHandler(messageType, commandHandler);
    }

    public <E extends EventHandler> CommandModelComponent registerEventHandler(@Nonnull QualifiedName messageType,
                                                                               @Nonnull E eventHandler) {
        return registerMessageHandler(messageType, eventHandler);
    }

    @Override
    public Set<QualifiedName> supportedMessages() {
        return delegate.supportedMessages();
    }
}

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO Playground for a MHC. Or keep as the main component to delegate to from, e.g., a CommandModelComponent?
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class GenericMessageHandlingComponent implements MessageHandlingComponent<Message<?>, Message<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ConcurrentHashMap<QualifiedName, MessageHandler<Message<?>, Message<?>>> messageHandlersByName;
    // TODO I would expect component-level interceptors to reside here as well. So, the @MessageHandlerInterceptor, for example.

    /**
     *
     */
    public GenericMessageHandlingComponent() {
        this.messageHandlersByName = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream<? extends Message<?>> handle(@Nonnull Message<?> message,
                                            @Nonnull ProcessingContext context) {
        QualifiedName messageType = message.name();
        // TODO add interceptor knowledge
        MessageHandler<Message<?>, Message<?>> messageHandler = messageHandlersByName.get(messageType);
        if (messageHandler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for message type [" + messageType + "]"
            ));
        }
        return messageHandler.apply(message, context);
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> GenericMessageHandlingComponent registerMessageHandler(
            @Nonnull Set<QualifiedName> names,
            @Nonnull H messageHandler
    ) {
        if (messageHandler != this) {
            names.forEach(messageType -> {
                //noinspection unchecked
                MessageHandler<Message<?>, Message<?>> oldHandler = messageHandlersByName.put(
                        messageType, (MessageHandler<Message<?>, Message<?>>) messageHandler
                );
                if (oldHandler != null) {
                    logger.warn("Duplicate message handler for message type [{}]. Replaced [{}] for [{}].",
                                messageType, oldHandler.name(), messageHandler.name());
                }
            });
        } else {
            logger.warn(
                    "Ignoring registration of [{}], as it is not recommend to register a MessageHandlingComponent with itself.",
                    messageHandler.name());
        }
        return this;
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> GenericMessageHandlingComponent registerMessageHandler(
            @Nonnull QualifiedName name,
            @Nonnull H messageHandler
    ) {
        return this.registerMessageHandler(Set.of(name), messageHandler);
    }

    public <C extends CommandHandler> GenericMessageHandlingComponent registerCommandHandler(
            @Nonnull QualifiedName messageType,
            @Nonnull C commandHandler
    ) {
        return registerMessageHandler(messageType, commandHandler);
    }

    public <E extends EventHandler> GenericMessageHandlingComponent registerEventHandler(
            @Nonnull QualifiedName messageType,
            @Nonnull E eventHandler
    ) {
        return registerMessageHandler(messageType, eventHandler);
    }

    public <Q extends QueryHandler> GenericMessageHandlingComponent registerQueryHandler(
            @Nonnull QualifiedName messageType,
            @Nonnull Q queryHandler
    ) {
        return registerMessageHandler(messageType, queryHandler);
    }

    @Override
    public Set<QualifiedName> supportedMessages() {
        return Set.copyOf(messageHandlersByName.keySet());
    }
}

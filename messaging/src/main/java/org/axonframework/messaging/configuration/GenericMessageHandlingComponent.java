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
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO This should be regarded as a playground object to verify the API. Feel free to remove, adjust, or replicate this class to your needs.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class GenericMessageHandlingComponent implements MessageHandlingComponent {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Map<QualifiedName, CommandHandler> commandHandlersByName;
    private final Map<QualifiedName, EventHandler> eventHandlersByName;
    private final Map<QualifiedName, QueryHandler> queryHandlersByName;
    // TODO I would expect component-level interceptors to reside here as well. So, the @MessageHandlerInterceptor, for example.

    /**
     * Default constructor of the {@code GenericMessageHandlingComponent}.
     */
    public GenericMessageHandlingComponent() {
        this.commandHandlersByName = new ConcurrentHashMap<>();
        this.eventHandlersByName = new ConcurrentHashMap<>();
        this.queryHandlersByName = new ConcurrentHashMap<>();
    }

    @Override
    public GenericMessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                     @Nonnull CommandHandler commandHandler) {
        return subscribe(name, commandHandler, commandHandlersByName);
    }

    @Override
    public GenericMessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                     @Nonnull EventHandler eventHandler) {
        return subscribe(name, eventHandler, eventHandlersByName);
    }

    @Override
    public GenericMessageHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                     @Nonnull QueryHandler queryHandler) {
        return subscribe(name, queryHandler, queryHandlersByName);
    }

    private <H extends MessageHandler> GenericMessageHandlingComponent subscribe(QualifiedName name,
                                                                                 H handler,
                                                                                 Map<QualifiedName, H> registry) {
        if (handler != this) {
            MessageHandler oldHandler = registry.put(name, handler);
            if (oldHandler != null) {
                logger.warn("Duplicate message handler for message name [{}]. Replaced [{}] for [{}].",
                            name, oldHandler, handler);
            }
        } else {
            logger.warn(
                    "Ignoring registration of [{}], as it is not recommend to subscribe a MessageHandlingComponent with itself.",
                    handler
            );
        }
        return this;
    }

    @Override
    public Set<QualifiedName> supportedCommands() {
        return Set.copyOf(commandHandlersByName.keySet());
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return Set.copyOf(eventHandlersByName.keySet());
    }

    @Override
    public Set<QualifiedName> supportedQueries() {
        return Set.copyOf(queryHandlersByName.keySet());
    }

    @Nonnull
    @Override
    public MessageStream<? extends CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> command,
                                                                   @Nonnull ProcessingContext context) {
        QualifiedName messageType = command.type().qualifiedName();
        // TODO add interceptor knowledge
        CommandHandler handler = commandHandlersByName.get(messageType);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for message type [" + messageType + "]"
            ));
        }
        return handler.handle(command, context);
    }

    @Nonnull
    @Override
    public MessageStream<NoMessage> handle(@Nonnull EventMessage<?> event,
                                           @Nonnull ProcessingContext context) {
        QualifiedName messageType = event.type().qualifiedName();
        // TODO add interceptor knowledge
        EventHandler handler = eventHandlersByName.get(messageType);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for message type [" + messageType + "]"
            ));
        }
        return handler.handle(event, context);
    }

    @Nonnull
    @Override
    public MessageStream<QueryResponseMessage<?>> handle(@Nonnull QueryMessage<?, ?> query,
                                                         @Nonnull ProcessingContext context) {
        QualifiedName messageType = query.type().qualifiedName();
        // TODO add interceptor knowledge
        QueryHandler handler = queryHandlersByName.get(messageType);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for message type [" + messageType + "]"
            ));
        }
        return handler.handle(query, context);
    }
}

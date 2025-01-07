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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryMessage;

import java.util.Set;

/**
 * TODO Should reside in the query module
 * TODO Should have an interface.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class QueryModelComponent implements MessageHandlingComponent<Message<?>, Message<?>> {

    private final EventHandlingComponent eventComponent;
    private final QueryHandlingComponent queryComponent;

    public QueryModelComponent() {
        this.eventComponent = new EventHandlingComponent();
        this.queryComponent = new QueryHandlingComponent();
    }

    @Nonnull
    @Override
    public MessageStream<? extends Message<?>> handle(@Nonnull Message<?> message, @Nonnull ProcessingContext context) {
        return switch (message) {
            case QueryMessage<?, ?> query -> queryComponent.handle(query, context);
            case EventMessage<?> event -> eventComponent.handle(event, context);
            default -> throw new IllegalArgumentException(
                    "Cannot handle message of type " + message.getClass()
                            + ". Only EventMessages and QueryMessages are supported."
            );
        };
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> QueryModelComponent subscribe(
            @Nonnull Set<QualifiedName> names, @Nonnull H messageHandler
    ) {
        if (messageHandler instanceof EventHandler eventHandler) {
            eventComponent.registerEventHandler(names, eventHandler);
            return this;
        }
        if (messageHandler instanceof QueryHandler queryHandler) {
            queryComponent.registerQueryHandler(names, queryHandler);
            return this;
        }
        throw new IllegalArgumentException("Cannot register command handlers on a query model component");
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> QueryModelComponent subscribe(
            @Nonnull QualifiedName name,
            @Nonnull H messageHandler
    ) {
        return subscribe(Set.of(name), messageHandler);
    }

    public <E extends EventHandler> QueryModelComponent registerEventHandler(@Nonnull QualifiedName messageType,
                                                                             @Nonnull E eventHandler) {
        eventComponent.registerEventHandler(messageType, eventHandler);
        return this;
    }

    public <Q extends QueryHandler> QueryModelComponent registerQueryHandler(@Nonnull QualifiedName messageType,
                                                                             @Nonnull Q queryHandler) {
        queryComponent.registerQueryHandler(messageType, queryHandler);
        return this;
    }

    @Override
    public Set<QualifiedName> supportedMessages() {
        Set<QualifiedName> supportedMessage = eventComponent.supportedMessages();
        supportedMessage.addAll(queryComponent.supportedMessages());
        return supportedMessage;
    }
}

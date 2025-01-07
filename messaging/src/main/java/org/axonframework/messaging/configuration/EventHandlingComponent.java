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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventHandlingComponent implements MessageHandlingComponent<EventMessage<?>, NoMessage> {

    private final ConcurrentHashMap<QualifiedName, EventHandler> eventHandlers;

    public EventHandlingComponent() {
        this.eventHandlers = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream<NoMessage> handle(@Nonnull EventMessage<?> event,
                                           @Nonnull ProcessingContext context) {
        QualifiedName name = event.name();
        // TODO add interceptor knowledge
        EventHandler handler = eventHandlers.get(name);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for event with name [" + name + "]"
            ));
        }
        // TODO - can we do something about this cast?
        return (MessageStream<NoMessage>) handler.apply(event, context);
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> EventHandlingComponent registerMessageHandler(
            @Nonnull Set<QualifiedName> names,
            @Nonnull H messageHandler
    ) {
        if (messageHandler instanceof CommandHandler) {
            throw new UnsupportedOperationException("Cannot register command handlers on an event handling component");
        }
        if (messageHandler instanceof QueryHandler) {
            throw new UnsupportedOperationException("Cannot register query handlers on an event handling component");
        }
        names.forEach(name -> eventHandlers.put(name, (EventHandler) messageHandler));
        return this;
    }

    @Override
    public <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> EventHandlingComponent registerMessageHandler(
            @Nonnull QualifiedName name,
            @Nonnull H messageHandler
    ) {
        return registerMessageHandler(Set.of(name), messageHandler);
    }

    /**
     *
     * @param names
     * @param eventHandler
     * @return
     * @param <E>
     */
    public <E extends EventHandler> EventHandlingComponent registerEventHandler(@Nonnull Set<QualifiedName> names,
                                                                                @Nonnull E eventHandler) {
        return registerMessageHandler(names, eventHandler);
    }

    /**
     * @param name
     * @param eventHandler
     * @param <E>
     * @return
     */
    public <E extends EventHandler> EventHandlingComponent registerEventHandler(@Nonnull QualifiedName name,
                                                                                @Nonnull E eventHandler) {
        return registerEventHandler(Set.of(name), eventHandler);
    }

    @Override
    public Set<QualifiedName> supportedMessages() {
        return eventHandlers.keySet();
    }
}

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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventHandlingComponent implements MessageHandlingComponent<EventHandler, EventMessage<?>, NoMessage> {

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
    public EventHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                            @Nonnull EventHandler handler) {
        names.forEach(name -> eventHandlers.put(name, Objects.requireNonNull(handler, "TODO")));
        return this;
    }

    @Override
    public EventHandlingComponent subscribe(@Nonnull QualifiedName name,
                                            @Nonnull EventHandler handler) {
        return subscribe(Set.of(name), handler);
    }

    @Override
    public Set<QualifiedName> supportedMessages() {
        return eventHandlers.keySet();
    }
}

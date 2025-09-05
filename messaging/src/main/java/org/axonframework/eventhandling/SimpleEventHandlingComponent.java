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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.configuration.DefaultEventHandlingComponentBuilder;
import org.axonframework.eventhandling.configuration.EventHandlingComponentBuilder;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple implementation of the {@link EventHandlingComponent}, containing a collection of
 * {@link EventHandler EventHandlers} to invoke on {@link #handle(EventMessage, ProcessingContext)}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class SimpleEventHandlingComponent implements EventHandlingComponent {

    private final ConcurrentHashMap<QualifiedName, List<EventHandler>> eventHandlers = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        QualifiedName name = event.type().qualifiedName();
        List<EventHandler> handlers = eventHandlers.get(name);
        if (handlers == null || handlers.isEmpty()) {
            return MessageStream.failed(
                    new NoHandlerForEventException(name, SimpleEventHandlingComponent.class.getName())
            );
        }
        MessageStream<Message> result = MessageStream.empty();
        for (var handler : handlers) {
            var handlerResult = handler.handle(event, context);
            result = result.concatWith(handlerResult);
        }
        return result.ignoreEntries().cast();
    }

    @Override
    public SimpleEventHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                                  @Nonnull EventHandler handler) {
        Objects.requireNonNull(handler, "The given handler cannot be null.");
        names.forEach(name -> eventHandlers.compute(name, (q, handlers) -> {
            if (handlers == null) {
                handlers = new CopyOnWriteArrayList<>();
            }
            handlers.add(handler);
            return handlers;
        }));
        return this;
    }

    @Override
    public SimpleEventHandlingComponent subscribe(@Nonnull QualifiedName name,
                                                  @Nonnull EventHandler handler) {
        return subscribe(Set.of(name), handler);
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return Set.copyOf(eventHandlers.keySet());
    }

    @Nonnull
    public static EventHandlingComponentBuilder.SequencingPolicyPhase builder() {
        return new DefaultEventHandlingComponentBuilder(new SimpleEventHandlingComponent());
    }
}

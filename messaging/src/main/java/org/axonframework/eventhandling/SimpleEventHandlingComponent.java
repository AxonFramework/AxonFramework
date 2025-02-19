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
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.configuration.NoMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO This should be regarded as a playground object to verify the API. Feel free to remove, adjust, or replicate this class to your needs.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class SimpleEventHandlingComponent implements EventHandlingComponent {

    private final ConcurrentHashMap<QualifiedName, EventHandler> eventHandlers;

    public SimpleEventHandlingComponent() {
        this.eventHandlers = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream<NoMessage> handle(@Nonnull EventMessage<?> event,
                                           @Nonnull ProcessingContext context) {
        QualifiedName name = event.type().qualifiedName();
        // TODO #3103 - add interceptor knowledge
        EventHandler handler = eventHandlers.get(name);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for event with name [" + name + "]"
            ));
        }
        return handler.handle(event, context);
    }

    @Override
    public SimpleEventHandlingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                                  @Nonnull EventHandler handler) {
        names.forEach(name -> eventHandlers.put(name, Objects.requireNonNull(handler, "TODO")));
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
}

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

package org.axonframework.eventsourcing;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO This should be regarded as a playground object to verify the API. Feel free to remove, adjust, or replicate this class to your needs.
 * Should it be like regular event handler?
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SimpleEventSourcingComponent implements EventSourcingComponent {

    private final ConcurrentHashMap<QualifiedName, IEventSourcingHandler> eventSourcingHandlers;

    public SimpleEventSourcingComponent() {
        this.eventSourcingHandlers = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream.Single<? extends Message<?>> source(@Nonnull EventMessage<?> event,
                                                             @Nonnull ProcessingContext context) {
        QualifiedName name = event.type().qualifiedName();
        // TODO #3103 - add interceptor knowledge
        var handler = eventSourcingHandlers.get(name);
        if (handler == null) {
            // TODO this would benefit from a dedicate exception
            return MessageStream.failed(new IllegalArgumentException(
                    "No handler found for event with name [" + name + "]"
            ));
        }
        return handler.source(event, context);
    }

    @Override
    public SimpleEventSourcingComponent subscribe(@Nonnull Set<QualifiedName> names,
                                                  @Nonnull EventHandler handler) {
        if (handler instanceof IEventSourcingHandler eventSourcingHandler) {
            names.forEach(name -> this.eventSourcingHandlers.put(name, eventSourcingHandler));
        } else {
            IEventSourcingHandler eventSourcingHandler = handler::handle;
            names.forEach(name -> this.eventSourcingHandlers.put(name, eventSourcingHandler));
        }
        return this;
    }

    @Override
    public SimpleEventSourcingComponent subscribe(@Nonnull QualifiedName name,
                                                  @Nonnull EventHandler handler) {
        return subscribe(Set.of(name), handler);
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return eventSourcingHandlers.keySet();
    }
}

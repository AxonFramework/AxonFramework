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
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Internal
public class ProcessorEventHandlingComponents {

    private final List<EventHandlingComponent> components;

    public ProcessorEventHandlingComponents(EventHandlingComponent... components) {
        this.components = Arrays.stream(components).toList();
    }

    public ProcessorEventHandlingComponents(List<EventHandlingComponent> components) {
        this.components = List.copyOf(components);
    }

    /**
     * Processes a batch of events in the processing context.
     * <p>
     * The result of handling is an {@link MessageStream.Empty empty stream}.
     *
     * @param events  The batch of event messages to be processed.
     * @param context The processing context in which the event messages are processed.
     * @return A stream of messages resulting from the processing of the event messages.
     */
    public MessageStream.Empty<Message<Void>> handle(List<? extends EventMessage<?>> events, ProcessingContext context) {
        MessageStream.Empty<Message<Void>> batchResult = MessageStream.empty();
        for (var event : events) {
            var eventResult = this.handle(event, context);
            batchResult = batchResult.concatWith(eventResult).ignoreEntries();
        }
        return batchResult;
//        MessageStream.Empty<Message<Void>> batchResult = MessageStream.empty();
//        for (var event : events) {
//            handle(event, context);
//        }
//        return batchResult;
    }

    /**
     * Handles the given {@code event} within the given {@code context}.
     * <p>
     * The result of handling is an {@link MessageStream.Empty empty stream}.
     *
     * @param event   The event to handle.
     * @param context The context to the given {@code event} is handled in.
     * @return An {@link MessageStream.Empty empty stream} containing nothing.
     */
    @Nonnull
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        MessageStream.Empty<Message<Void>> result = MessageStream.empty();
        for (var component : components) {
            if (component.supports(event.type().qualifiedName())) {
                var componentResult = component.handle(event, context);
                result = result.concatWith(componentResult).ignoreEntries();
            }
        }
        return result;
    }

    public Set<QualifiedName> supportedEvents() {
        return components.stream()
                         .flatMap(c -> c.supportedEvents().stream())
                         .collect(Collectors.toSet());
    }

    public boolean supports(@Nonnull QualifiedName eventName) {
        return supportedEvents().contains(eventName);
    }

    public Set<Object> sequenceIdentifiersFor(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        return components.stream()
                         .map(c -> c.sequenceIdentifierFor(event, context))
                         .collect(Collectors.toSet());
    }

    public List<EventHandlingComponent> toList() {
        return components;
    }
}

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

    private final List<SequencingEventHandlingComponent> components;

    public ProcessorEventHandlingComponents(@Nonnull EventHandlingComponent... components) {
        this(Arrays.stream(components).toList());
    }

    public ProcessorEventHandlingComponents(@Nonnull List<EventHandlingComponent> components) {
        this.components = components.stream()
                                    .map(c -> c instanceof SequencingEventHandlingComponent seq
                                            ? seq
                                            : new SequencingEventHandlingComponent(c)
                                    ).toList();
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
    @Nonnull
    public MessageStream.Empty<Message<Void>> handle(
            @Nonnull List<? extends EventMessage<?>> events,
            @Nonnull ProcessingContext context
    ) {
        MessageStream<Message<Void>> batchResult = MessageStream.empty().cast();
        for (var event : events) {
            var eventResult = handle(event, context);
            batchResult = batchResult.concatWith(eventResult.cast());
        }
        return batchResult.ignoreEntries()
                          .cast();
    }

    /**
     * Handles the given {@code event} within the given {@code context}.
     * <p>
     * The result of handling is an {@link MessageStream.Empty empty stream}.
     * <p>
     * Sequencing of event processing is not preserved between different event handling components. This is intentional,
     * as they may have different sequencing policies.
     *
     * @param event   The event to handle.
     * @param context The context to the given {@code event} is handled in.
     * @return An {@link MessageStream.Empty empty stream} containing nothing.
     */
    @Nonnull
    private MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                      @Nonnull ProcessingContext context
    ) {
        MessageStream<Message<Void>> result = MessageStream.empty();
        for (var component : components) {
            if (component.supports(event.type().qualifiedName())) {
                var componentResult = component.handle(event, context);
                result = result.concatWith(componentResult);
            }
        }
        return result.ignoreEntries().cast();
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

    public List<SequencingEventHandlingComponent> toList() {
        return components;
    }
}

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

/**
 * Internal class for managing multiple {@link EventHandlingComponent} instances and processing event messages through
 * them. Each event handling component is wrapped in a {@link SequencingEventHandlingComponent} to ensure proper
 * sequencing where required.
 * <p>
 * Key responsibilities include: - Distributing event messages to the associated {@link EventHandlingComponent}
 * instances. - Ensuring event handling sequencing policies are respected when applicable. - Determining support for
 * specific event types across the managed components.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class ProcessorEventHandlingComponents {

    private final List<? extends EventHandlingComponent> components;

    /**
     * Constructs a {@code ProcessorEventHandlingComponents} instance by converting the provided varargs of
     * {@link EventHandlingComponent}s into a list and passing them to the corresponding constructor.
     *
     * @param components A varargs array of {@link EventHandlingComponent}s to be used for event processing.
     *                   Must not be null and is converted into a list of {@link SequencingEventHandlingComponent}s
     *                   if necessary.
     */
    public ProcessorEventHandlingComponents(@Nonnull EventHandlingComponent... components) {
        this(Arrays.stream(components).toList());
    }

    /**
     * Constructs a {@code ProcessorEventHandlingComponents} instance by wrapping the provided list of
     * {@link EventHandlingComponent}s in SequencingEventHandlingComponent instances for sequential event handling
     * where needed.
     *
     * @param components The list of {@link EventHandlingComponent}s to be used for event processing.
     *                   Must not be null and is transformed into a list of {@link SequencingEventHandlingComponent}s
     *                   if necessary.
     */
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
     * The result of handling is an {@link MessageStream.Empty empty stream}. It's guaranteed that the events with same
     * {@link #sequenceIdentifiersFor(EventMessage, ProcessingContext)} value are processed by a single component in the
     * order they are received, but the sequencing of event processing is not preserved between different event handling
     * components. This is intentional, as they may have different sequencing policies.
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

    /**
     * Retrieves a set of all event names supported by the components comprising this ProcessorEventHandlingComponents
     * instance. Each event is referenced through a QualifiedName.
     *
     * @return A set of QualifiedName objects representing the supported events.
     */
    public Set<QualifiedName> supportedEvents() {
        return components.stream()
                         .flatMap(c -> c.supportedEvents().stream())
                         .collect(Collectors.toSet());
    }

    /**
     * Checks if the specified event name is supported by any of the components.
     *
     * @param eventName The qualified name of the event to be checked. Must not be null.
     * @return true if the event name is supported, false otherwise.
     */
    public boolean supports(@Nonnull QualifiedName eventName) {
        return components.stream().anyMatch(c -> c.supports(eventName));
    }

    /**
     * Retrieves a set of sequence identifiers for the given event message and processing context.
     * Each identifier represents a sequence property determined by the components within this instance.
     *
     * @param event   The event message for which the sequence identifiers are to be determined.
     *                Must not be null.
     * @param context The processing context in which the sequence identifiers are evaluated.
     *                Must not be null.
     * @return A set of sequence identifiers associated with the given event and context.
     */
    public Set<Object> sequenceIdentifiersFor(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        return components.stream()
                         .map(c -> c.sequenceIdentifierFor(event, context))
                         .collect(Collectors.toSet());
    }
}

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
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Legacy adapter that wraps an {@link EventHandlerInvoker} to implement the {@link EventHandlingComponent} interface.
 * This adapter allows deprecated {@code EventHandlerInvoker} implementations to work with the new event handling
 * component architecture.
 * <p>
 * This class is intended as a migration helper and should be used temporarily while transitioning from
 * {@code EventHandlerInvoker} to {@code EventHandlingComponent}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Deprecated(since = "5.0.0", forRemoval = true)
public class LegacyEventHandlingComponent implements EventHandlingComponent {

    private final EventHandlerInvoker eventHandlerInvoker;

    /**
     * Constructs a {@link LegacyEventHandlingComponent} that wraps the given {@code eventHandlerInvoker}.
     *
     * @param eventHandlerInvoker The {@link EventHandlerInvoker} to wrap.
     */
    public LegacyEventHandlingComponent(@Nonnull EventHandlerInvoker eventHandlerInvoker) {
        this.eventHandlerInvoker = eventHandlerInvoker;
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event, 
                                                     @Nonnull ProcessingContext context) {
        try {
            Segment segment = Segment.fromContext(context).orElse(Segment.ROOT_SEGMENT);
            eventHandlerInvoker.handle(event, context, segment);
            return MessageStream.empty();
        } catch (Exception e) {
            return MessageStream.failed(e);
        }
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return eventHandlerInvoker.supportedEventTypes()
                                  .stream()
                                  .map(QualifiedName::new)
                                  .collect(Collectors.toSet());
    }

    @Override
    public boolean supports(@Nonnull QualifiedName eventName) {
        Set<QualifiedName> supportedEvents = supportedEvents();
        return supportedEvents.contains(eventName);
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage<?> event) {
        return switch (eventHandlerInvoker) {
            case MultiEventHandlerInvoker multiInvoker when !multiInvoker.delegates().isEmpty() ->
                    Optional.ofNullable(multiInvoker.delegates().getFirst())
                            .filter(SimpleEventHandlerInvoker.class::isInstance)
                            .map(SimpleEventHandlerInvoker.class::cast)
                            .flatMap(invoker -> invoker.getSequencingPolicy().getSequenceIdentifierFor(event))
                            .orElseGet(event::identifier);
            case SimpleEventHandlerInvoker simpleInvoker ->
                    simpleInvoker.getSequencingPolicy().getSequenceIdentifierFor(event).orElseGet(event::identifier);
            default -> event.identifier();
        };
    }

    @Override
    public EventHandlerRegistry subscribe(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler) {
        // EventHandlerInvoker doesn't support dynamic subscription
        throw new UnsupportedOperationException(
                "Dynamic subscription is not supported by LegacyEventHandlingComponent. " +
                "This is a legacy adapter for EventHandlerInvoker which doesn't support runtime registration."
        );
    }

    /**
     * Returns the wrapped {@link EventHandlerInvoker}.
     *
     * @return The wrapped {@code EventHandlerInvoker}.
     */
    public EventHandlerInvoker getEventHandlerInvoker() {
        return eventHandlerInvoker;
    }
}

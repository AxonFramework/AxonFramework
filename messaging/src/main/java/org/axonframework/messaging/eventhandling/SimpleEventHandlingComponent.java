/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.replay.ResetContext;
import org.axonframework.messaging.eventhandling.replay.ResetHandler;
import org.axonframework.messaging.eventhandling.replay.ResetHandlerRegistry;
import org.axonframework.messaging.eventhandling.sequencing.HierarchicalSequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPerAggregatePolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPolicy;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SequenceOverridingEventHandlingComponent;

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

    private static final SequencingPolicy DEFAULT_SEQUENCING_POLICY = new HierarchicalSequencingPolicy(
            SequentialPerAggregatePolicy.instance(),
            SequentialPolicy.INSTANCE
    );

    private final ConcurrentHashMap<QualifiedName, List<EventHandler>> eventHandlers = new ConcurrentHashMap<>();
    private final SequencingPolicy sequencingPolicy;

    private final Set<ResetHandler> resetHandlers = ConcurrentHashMap.newKeySet();

    /**
     * Initializes a {@code SimpleEventHandlingComponent} with no {@link EventHandler}s and default
     * {@link SequentialPolicy}.
     */
    public SimpleEventHandlingComponent() {
        this.sequencingPolicy = DEFAULT_SEQUENCING_POLICY;
    }

    /**
     * Initializes a {@code SimpleEventHandlingComponent} with no {@link EventHandler}s and the given
     * {@code sequencingPolicy}.
     *
     * @param sequencingPolicy the {@link SequencingPolicy} to use for sequencing events.
     */
    public SimpleEventHandlingComponent(@Nonnull SequencingPolicy sequencingPolicy) {
        this.sequencingPolicy = Objects.requireNonNull(sequencingPolicy, "Sequencing Policy may not be null.");
    }

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

    /**
     * {@inheritDoc}
     * <p>
     * The implementation automatically chooses the appropriate sequencing policy based on the event context:
     * <ul>
     *     <li>If the aggregate identifier is present in the {@link ProcessingContext} (i.e., the event is an aggregate
     *         event), it uses {@link SequentialPerAggregatePolicy} to ensure events for the same aggregate are
     *         processed sequentially while allowing concurrent processing of events from different aggregates.</li>
     *     <li>If no aggregate identifier is present (i.e., the event is not an aggregate event), it uses
     *         {@link SequentialPolicy} which ensures all events are processed sequentially (no concurrency) as the
     *         safest default option.</li>
     * </ul>
     * <p>
     * Override this method to provide custom sequencing behavior. Or use a
     * {@link SequenceOverridingEventHandlingComponent} if you cannot inherit from a certain
     * {@code EventHandlingComponent} implementation.
     * <p>
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        var qualifiedName = event.type().qualifiedName();
        List<EventHandler> handlers = eventHandlers.get(qualifiedName);

        if (handlers == null || handlers.isEmpty()) {
            return sequencingPolicy.getSequenceIdentifierFor(event, context).get();
        }

        return handlers.stream()
                       .filter(EventHandlingComponent.class::isInstance)
                       .map(EventHandlingComponent.class::cast)
                       .findFirst()
                       .map(component -> component.sequenceIdentifierFor(event, context))
                       .orElse(sequencingPolicy.getSequenceIdentifierFor(event, context).get());
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull ResetContext resetContext, @Nonnull ProcessingContext context) {
        MessageStream<Message> result = MessageStream.empty();

        for (ResetHandler handler : resetHandlers) {
            MessageStream<Message> handlerResult = handler.handle(resetContext, context);
            result = result.concatWith(handlerResult);
        }

        return result.ignoreEntries().cast();
    }

    @Nonnull
    @Override
    public ResetHandlerRegistry subscribe(@Nonnull ResetHandler resetHandler) {
        resetHandlers.add(resetHandler);
        return this;
    }
}

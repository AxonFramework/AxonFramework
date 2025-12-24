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

package org.axonframework.messaging.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Assert;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.SequenceOverridingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.sequencing.HierarchicalSequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequencingPolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPerAggregatePolicy;
import org.axonframework.messaging.eventhandling.sequencing.SequentialPolicy;

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
public class SimpleEventHandlingComponent implements
        EventHandlingComponent,
        EventHandlerRegistry<SimpleEventHandlingComponent> {

    private static final SequencingPolicy DEFAULT_SEQUENCING_POLICY = new HierarchicalSequencingPolicy(
            SequentialPerAggregatePolicy.instance(),
            SequentialPolicy.INSTANCE
    );

    private final String name;
    private final ConcurrentHashMap<QualifiedName, List<EventHandler>> eventHandlers = new ConcurrentHashMap<>();
    private final SequencingPolicy sequencingPolicy;

    /**
     * Instantiates a simple {@link EventHandlingComponent} that is able to handle events and delegate them to
     * subcomponents.
     * <p>
     * Uses a default sequencing policy that will first try for the {@link SequentialPerAggregatePolicy}, falling
     * back to the {@link SequentialPolicy} when the former returns no sequence value.
     *
     * @param name The name of the component, used for {@link DescribableComponent describing} the component.
     * @return A simple {@link EventHandlingComponent} instance with the given {@code name}.
     */
    public static SimpleEventHandlingComponent create(@Nonnull String name) {
        return create(name, DEFAULT_SEQUENCING_POLICY);
    }

    /**
     * Instantiates a simple {@link EventHandlingComponent} that is able to handle events and delegate them to
     * subcomponents, using the given {@code sequencingPolicy} to decide how to sequence incoming events.
     *
     * @param name             The name of the component, used for {@link DescribableComponent describing} the
     *                         component.
     * @param sequencingPolicy The {@link SequencingPolicy} to use for sequencing events through, for example,
     *                         {@link #sequenceIdentifierFor(EventMessage, ProcessingContext)}.
     * @return A simple {@link EventHandlingComponent} instance with the given {@code name}.
     */
    public static SimpleEventHandlingComponent create(@Nonnull String name,
                                                      @Nonnull SequencingPolicy sequencingPolicy) {
        return new SimpleEventHandlingComponent(name, sequencingPolicy);
    }

    private SimpleEventHandlingComponent(@Nonnull String name,
                                         @Nonnull SequencingPolicy sequencingPolicy) {
        this.name = Assert.nonEmpty(name, "The name may not be null or empty.");
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

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("name", name);
        descriptor.describeProperty("eventHandlers", eventHandlers);
        descriptor.describeProperty("sequencingPolicy", sequencingPolicy);
    }
}

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
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Decorator implementation of {@link EventHandlingComponent} that uses a configurable {@link SequencingPolicy} to
 * determine the sequence identifier for events, while delegating all other operations to an underlying
 * {@link EventHandlingComponent}.
 * <p>
 * This component first attempts to determine sequence identification using the configured sequencing policy. If the
 * policy returns {@link Optional#empty()}, only then it falls back to the delegate component's sequence identifier.
 * This allows for overriding the sequencing behavior of the underlying component.
 * <p>
 *
 * @author Mateusz Nowak
 * @see SequencingPolicy
 * @see EventHandlingComponent
 * @since 5.0.0
 */
@Internal
public class SequenceOverridingEventHandlingComponent implements EventHandlingComponent {

    private final SequencingPolicy sequencingPolicy;
    private final EventHandlingComponent delegate;


    /**
     * Creates a new {@code SequenceOverridingEventHandlingComponent} that uses the given {@code sequencingPolicy} to
     * override sequence identification while delegating all other operations to the {@code delegate} component.
     *
     * @param sequencingPolicy The policy to use for determining sequence identifiers for events.
     * @param delegate         The underlying event handling component to delegate operations to.
     */
    public SequenceOverridingEventHandlingComponent(@Nonnull SequencingPolicy sequencingPolicy,
                                                    @Nonnull EventHandlingComponent delegate) {
        this.sequencingPolicy = requireNonNull(sequencingPolicy, "SequencingPolicy may not be null");
        this.delegate = requireNonNull(delegate, "Delegate EventHandlingComponent may not be null");
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        requireNonNull(event, "Event Message may not be null");
        return sequencingPolicy.getSequenceIdentifierFor(event)
                               .orElseGet(() -> delegate.sequenceIdentifierFor(event, context));
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return delegate.supportedEvents();
    }

    @Override
    public boolean supports(@Nonnull QualifiedName eventName) {
        return delegate.supports(eventName);
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        return delegate.handle(event, context);
    }

    @Override
    public EventHandlerRegistry subscribe(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler) {
        return delegate.subscribe(name, eventHandler);
    }

    @Override
    public EventHandlerRegistry subscribe(@Nonnull Set<QualifiedName> names, @Nonnull EventHandler eventHandler) {
        return delegate.subscribe(names, eventHandler);
    }

    @Override
    public EventHandlerRegistry subscribe(@Nonnull EventHandlingComponent handlingComponent) {
        return delegate.subscribe(handlingComponent);
    }
}

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
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Decorator implementation of {@link EventHandlingComponent} that uses a configurable {@link SequencingPolicy} as
 * fallback to determine the sequence identifier for events, while delegating all other operations to an underlying
 * {@link EventHandlingComponent}.
 * <p>
 * This component first delegates sequence identification to the underlying component. If the delegate returns
 * {@link Optional#empty()}, then the configured sequencing policy is used as a fallback to determine the sequence
 * identifier. This allows for customizing the sequencing behavior without overriding existing sequencing logic.
 * <p>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 * @see SequencingPolicy
 * @see EventHandlingComponent
 */
public class SequencingEventHandlingComponent implements EventHandlingComponent {

    private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;
    private final EventHandlingComponent delegate;

    /**
     * Creates a new {@link SequencingEventHandlingComponent} that uses the given {@code sequencingPolicy}
     * as fallback for sequence identification while delegating all other operations to the {@code delegate} component.
     *
     * @param sequencingPolicy The policy to use as fallback for determining sequence identifiers for events.
     * @param delegate        The underlying event handling component to delegate operations to.
     */
    public SequencingEventHandlingComponent(@Nonnull SequencingPolicy<? super EventMessage<?>> sequencingPolicy,
                                            @Nonnull EventHandlingComponent delegate) {
        this.sequencingPolicy = requireNonNull(sequencingPolicy, "SequencingPolicy may not be null");
        this.delegate = requireNonNull(delegate, "Delegate EventHandlingComponent may not be null");
    }

    /**
     * Returns the sequencing policy used by this component.
     *
     * @return The {@link SequencingPolicy} used for determining sequence identifiers.
     */
    public SequencingPolicy<? super EventMessage<?>> getSequencingPolicy() {
        return sequencingPolicy;
    }

    @Override
    public Optional<Object> sequenceIdentifierFor(@Nonnull EventMessage<?> event) {
        requireNonNull(event, "Event Message may not be null");
        Optional<Object> delegateResult = delegate.sequenceIdentifierFor(event);
        return delegateResult.isPresent() ? delegateResult : sequencingPolicy.getSequenceIdentifierFor(event);
    }

    @Override
    public Set<QualifiedName> supportedEvents() {
        return delegate.supportedEvents();
    }

    @Override
    public boolean isSupported(QualifiedName eventName) {
        return delegate.isSupported(eventName);
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
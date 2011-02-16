/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.annotation;

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.eventhandling.annotation.AnnotationEventHandlerInvoker;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;

import java.io.IOException;
import java.util.UUID;

/**
 * Convenience super type for aggregate roots that have their event handler methods annotated with the {@link
 * org.axonframework.eventhandling.annotation.EventHandler} annotation.
 * <p/>
 * Implementations can call the {@link #apply(DomainEvent)} method to have an event applied. S *
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.annotation.EventHandler
 * @since 0.1
 */
public abstract class AbstractAnnotatedAggregateRoot extends AbstractEventSourcedAggregateRoot {

    private static final long serialVersionUID = -1206026570158467937L;
    private transient AnnotationEventHandlerInvoker eventHandlerInvoker;

    /**
     * Initialize the aggregate root with a random identifier.
     */
    protected AbstractAnnotatedAggregateRoot() {
        super();
        eventHandlerInvoker = new AnnotationEventHandlerInvoker(this);
    }

    /**
     * Initializes the aggregate root using the provided aggregate identifier.
     *
     * @param identifier the identifier of this aggregate
     */
    protected AbstractAnnotatedAggregateRoot(AggregateIdentifier identifier) {
        super(identifier);
        eventHandlerInvoker = new AnnotationEventHandlerInvoker(this);
    }

    /**
     * Initializes the aggregate root using the provided aggregate identifier.
     *
     * @param identifier the identifier of this aggregate
     * @deprecated Use {@link #AbstractEventSourcedAggregateRoot(org.axonframework.domain.AggregateIdentifier)}
     */
    @Deprecated
    protected AbstractAnnotatedAggregateRoot(UUID identifier) {
        this(new UUIDAggregateIdentifier(identifier));
    }

    /**
     * Calls the appropriate {@link org.axonframework.eventhandling.annotation.EventHandler} annotated handler with the
     * provided event.
     *
     * @param event The event to handle
     * @see org.axonframework.eventhandling.annotation.EventHandler
     */
    @Override
    protected void handle(DomainEvent event) {
        eventHandlerInvoker.invokeEventHandlerMethod(event);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        eventHandlerInvoker = new AnnotationEventHandlerInvoker(this);
    }
}

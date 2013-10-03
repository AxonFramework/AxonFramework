/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.annotation;

import org.axonframework.common.configuration.AnnotationConfiguration;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventhandling.annotation.AnnotationEventHandlerInvoker;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcedEntity;

import java.util.Collection;
import javax.persistence.MappedSuperclass;

/**
 * Convenience super type for aggregate roots that have their event handler methods annotated with the {@link
 * org.axonframework.eventhandling.annotation.EventHandler} annotation.
 * <p/>
 * Implementations can call the {@link #apply(Object)} method to have an event applied.
 *
 * @param <I> The type of the identifier of this aggregate
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.annotation.EventHandler
 * @since 0.1
 */
@MappedSuperclass
public abstract class AbstractAnnotatedAggregateRoot<I> extends AbstractEventSourcedAggregateRoot<I> {

    private static final long serialVersionUID = -1206026570158467937L;
    private transient AnnotationEventHandlerInvoker eventHandlerInvoker; // NOSONAR
    private transient AggregateAnnotationInspector inspector; // NOSONAR

    /**
     * Calls the appropriate {@link org.axonframework.eventhandling.annotation.EventHandler} annotated handler with the
     * provided event.
     *
     * @param event The event to handle
     * @see org.axonframework.eventhandling.annotation.EventHandler
     */
    @Override
    protected void handle(DomainEventMessage event) {
        ensureInspectorInitialized();
        ensureInvokerInitialized();
        eventHandlerInvoker.invokeEventHandlerMethod(event);
    }

    @SuppressWarnings("unchecked")
    @Override
    public I getIdentifier() {
        ensureInspectorInitialized();
        return inspector.getIdentifier(this);
    }

    @Override
    protected Collection<EventSourcedEntity> getChildEntities() {
        ensureInspectorInitialized();
        return inspector.getChildEntities(this);
    }

    private void ensureInvokerInitialized() {
        if (eventHandlerInvoker == null) {
            eventHandlerInvoker = inspector.createEventHandlerInvoker(this);
        }
    }

    private void ensureInspectorInitialized() {
        if (inspector == null) {
            final Class<? extends AbstractAnnotatedAggregateRoot> aggregateType = getClass();
            inspector = AggregateAnnotationInspector.getInspector(
                    aggregateType, AnnotationConfiguration.readFor(aggregateType).getParameterResolverFactory());
        }
    }
}

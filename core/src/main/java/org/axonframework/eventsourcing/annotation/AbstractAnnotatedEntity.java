/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.annotation.MessageHandlerInvoker;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventsourcing.AbstractEventSourcedEntity;
import org.axonframework.eventsourcing.EventSourcedEntity;

import java.util.Collection;

/**
 * Convenience super type for entities (other than aggregate roots) that have their event handler methods annotated
 * with the {@link org.axonframework.eventhandling.annotation.EventHandler} annotation.
 * <p/>
 * Note that each entity receives <strong>all</strong> events applied in the entire aggregate. Entities are responsible
 * for filtering out the actual events to take action on.
 * <p/>
 * If this entity is a child of another <code>AbstractAnnotatedEntity</code> or
 * <code>AbstractAnnotatedAggregateRoot</code>, the field that this entity is stored in should be annotated with {@link
 * EventSourcedMember}. Alternatively, the {@link
 * org.axonframework.eventsourcing.AbstractEventSourcedEntity#getChildEntities()} or {@link
 * org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot#getChildEntities()} should return a collection
 * containing this entity instance.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractAnnotatedEntity extends AbstractEventSourcedEntity {

    private transient AggregateAnnotationInspector inspector;
    private transient MessageHandlerInvoker eventHandlerInvoker;

    /**
     * Default constructor.
     */
    protected AbstractAnnotatedEntity() {
    }

    /**
     * Calls the appropriate handler method with the provided event.
     *
     * @param event The event to handle
     * @see org.axonframework.eventsourcing.annotation.EventSourcingHandler
     * @see org.axonframework.eventhandling.annotation.EventHandler
     */
    @Override
    protected void handle(DomainEventMessage event) {
        // some deserialization mechanisms don't use the default constructor to initialize a class.
        ensureInspectorInitialized();
        ensureInvokerInitialized();
        eventHandlerInvoker.invokeHandlerMethod(event);
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
            final ParameterResolverFactory parameterResolverFactory = createParameterResolverFactory();
            inspector = AggregateAnnotationInspector.getInspector(getClass(), parameterResolverFactory);
        }
    }

    protected ParameterResolverFactory createParameterResolverFactory() {
        return ((AbstractAnnotatedAggregateRoot) getAggregateRoot()).createParameterResolverFactory();
    }
}

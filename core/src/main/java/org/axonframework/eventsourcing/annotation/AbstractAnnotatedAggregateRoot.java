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

import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.MessageHandlerInvoker;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.EventSourcedEntity;
import org.axonframework.unitofwork.CurrentUnitOfWork;

import java.util.Collection;
import javax.persistence.MappedSuperclass;

/**
 * Convenience super type for aggregate roots that have their event handler methods annotated with the {@link
 * org.axonframework.eventsourcing.annotation.EventSourcingHandler} annotation (and {@link
 * org.axonframework.eventhandling.annotation.EventHandler} for backwards compatibility).
 * <p/>
 * Implementations can call the {@link #apply(Object)} method to have an event applied.
 *
 * @param <I> The type of the identifier of this aggregate
 * @author Allard Buijze
 * @see org.axonframework.eventsourcing.annotation.EventSourcingHandler
 * @see org.axonframework.eventhandling.annotation.EventHandler
 * @since 0.1
 */
@MappedSuperclass
public abstract class AbstractAnnotatedAggregateRoot<I> extends AbstractEventSourcedAggregateRoot<I> {

    private static final long serialVersionUID = -1206026570158467937L;
    private transient MessageHandlerInvoker eventHandlerInvoker; // NOSONAR
    private transient AggregateAnnotationInspector inspector; // NOSONAR
    private transient ParameterResolverFactory parameterResolverFactory; // NOSONAR

    /**
     * Calls the appropriate handler method with the provided event.
     *
     * @param event The event to handle
     * @see org.axonframework.eventsourcing.annotation.EventSourcingHandler
     * @see org.axonframework.eventhandling.annotation.EventHandler
     */
    @Override
    protected void handle(DomainEventMessage event) {
        ensureInspectorInitialized();
        ensureInvokerInitialized();
        eventHandlerInvoker.invokeHandlerMethod(event);
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
            ensureInspectorInitialized();
            eventHandlerInvoker = inspector.createEventHandlerInvoker(this);
        }
    }

    private void ensureInspectorInitialized() {
        if (inspector == null) {
            final Class<? extends AbstractAnnotatedAggregateRoot> aggregateType = getClass();
            inspector = AggregateAnnotationInspector.getInspector(aggregateType, createParameterResolverFactory());
        }
    }

    /**
     * Creates (or returns) a ParameterResolverFactory which is used by this aggregate root to resolve the parameters
     * for @EventSourcingHandler annotated methods.
     * <p/>
     * Unless a specific ParameterResolverFactory has ben registered using {@link #registerParameterResolverFactory(org.axonframework.common.annotation.ParameterResolverFactory)},
     * this implementation uses the aggregate root's class loader to find parameter resolver factory implementations
     * on the classpath.
     *
     * @return the parameter resolver with which to resolve parameters for event handler methods.
     *
     * @see org.axonframework.common.annotation.ClasspathParameterResolverFactory
     */
    protected ParameterResolverFactory createParameterResolverFactory() {
        if (parameterResolverFactory == null && CurrentUnitOfWork.isStarted()) {
            parameterResolverFactory = CurrentUnitOfWork.get().getResource(ParameterResolverFactory.class.getName());
        }
        if (parameterResolverFactory == null) {
            parameterResolverFactory = ClasspathParameterResolverFactory.forClass(getClass());
        }
        return parameterResolverFactory;
    }

    /**
     * Registers the given <code>parameterResolverFactory</code> with this aggregate instance. This factory will
     * provide the resolvers necessary for the
     * {@link org.axonframework.eventhandling.annotation.EventHandler @EventHandler} and {@link
     * org.axonframework.eventsourcing.annotation.EventSourcingHandler @EventSourcingHandler}
     * annoated metods in all members of this aggregate.
     *
     * @param parameterResolverFactory The factory to provide resolvers for parameters of annotated event handlers
     */
    public void registerParameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
        this.parameterResolverFactory = parameterResolverFactory;
    }
}

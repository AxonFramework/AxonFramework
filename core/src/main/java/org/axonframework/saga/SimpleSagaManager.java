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

package org.axonframework.saga;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Simple SagaManager implementation. This implementation requires the Event that should cause new Saga's to be
 * created,
 * to be registered using {@link #setEventsToAlwaysCreateNewSagasFor(java.util.List)} and {@link
 * #setEventsToOptionallyCreateNewSagasFor(java.util.List)}.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SimpleSagaManager extends AbstractSagaManager {

    private final AssociationValueResolver associationValueResolver;

    private List<Class<?>> eventsToAlwaysCreateNewSagasFor = Collections.emptyList();
    private List<Class<?>> eventsToOptionallyCreateNewSagasFor = Collections.emptyList();
    private final Class<? extends Saga> sagaType;

    /**
     * Initialize a SimpleSagaManager backed by the given resources, using a GenericSagaFactory.
     *
     * @param sagaType                 The type of Saga managed by this SagaManager
     * @param sagaRepository           The repository providing access to Saga instances
     * @param associationValueResolver The instance providing AssociationValues for incoming Events
     * @param eventBus                 The event bus that the manager should register to
     * @deprecated use {@link #SimpleSagaManager(Class, SagaRepository, AssociationValueResolver)} and register using
     * {@link EventBus#subscribe(org.axonframework.eventhandling.EventListener)}
     */
    @Deprecated
    public SimpleSagaManager(Class<? extends Saga> sagaType, SagaRepository sagaRepository,
                             AssociationValueResolver associationValueResolver,
                             EventBus eventBus) {
        this(sagaType, sagaRepository, associationValueResolver, new GenericSagaFactory(), eventBus);
    }

    /**
     * Initialize a SimpleSagaManager backed by the given resources.
     *
     * @param sagaType                 The type of Saga managed by this SagaManager
     * @param sagaRepository           The repository providing access to Saga instances
     * @param associationValueResolver The instance providing AssociationValues for incoming Events
     * @param sagaFactory              The factory creating new Saga instances
     * @param eventBus                 The event bus that the manager should register to
     * @deprecated use {@link #SimpleSagaManager(Class, SagaRepository, AssociationValueResolver, SagaFactory)} and
     * register using {@link EventBus#subscribe(org.axonframework.eventhandling.EventListener)}
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public SimpleSagaManager(Class<? extends Saga> sagaType, SagaRepository sagaRepository,
                             AssociationValueResolver associationValueResolver, SagaFactory sagaFactory,
                             EventBus eventBus) {
        super(eventBus, sagaRepository, sagaFactory, sagaType);
        this.sagaType = sagaType;
        this.associationValueResolver = associationValueResolver;
    }

    /**
     * Initialize a SimpleSagaManager backed by the given resources, using a GenericSagaFactory.
     *
     * @param sagaType                 The type of Saga managed by this SagaManager
     * @param sagaRepository           The repository providing access to Saga instances
     * @param associationValueResolver The instance providing AssociationValues for incoming Events
     */
    public SimpleSagaManager(Class<? extends Saga> sagaType, SagaRepository sagaRepository,
                             AssociationValueResolver associationValueResolver) {
        this(sagaType, sagaRepository, associationValueResolver, new GenericSagaFactory());
    }

    /**
     * Initialize a SimpleSagaManager backed by the given resources.
     *
     * @param sagaType                 The type of Saga managed by this SagaManager
     * @param sagaRepository           The repository providing access to Saga instances
     * @param associationValueResolver The instance providing AssociationValues for incoming Events
     * @param sagaFactory              The factory creating new Saga instances
     */
    @SuppressWarnings("unchecked")
    public SimpleSagaManager(Class<? extends Saga> sagaType, SagaRepository sagaRepository,
                             AssociationValueResolver associationValueResolver, SagaFactory sagaFactory) {
        super(sagaRepository, sagaFactory, sagaType);
        this.sagaType = sagaType;
        this.associationValueResolver = associationValueResolver;
    }

    @Override
    protected SagaInitializationPolicy getSagaCreationPolicy(Class<? extends Saga> type, EventMessage event) {
        AssociationValue initialAssociationValue = initialAssociationValue(event);
        if (isAssignableClassIn(event.getPayloadType(), eventsToOptionallyCreateNewSagasFor)) {
            return new SagaInitializationPolicy(SagaCreationPolicy.IF_NONE_FOUND, initialAssociationValue);
        } else if (isAssignableClassIn(event.getPayloadType(), eventsToAlwaysCreateNewSagasFor)) {
            return new SagaInitializationPolicy(SagaCreationPolicy.ALWAYS, initialAssociationValue);
        } else {
            return SagaInitializationPolicy.NONE;
        }
    }

    @Override
    protected Set<AssociationValue> extractAssociationValues(Class<? extends Saga> type, EventMessage event) {
        return associationValueResolver.extractAssociationValues(event);
    }

    /**
     * Returns the association value to assign to a Saga when the given <code>event</code> triggers the creation of
     * a new instance. If there are no creation handlers for the given <code>event</code>, <code>null</code> is
     * returned.
     *
     * @param event The event to resolve the initial association for
     * @return The association value to assign, or <code>null</code>
     */
    protected AssociationValue initialAssociationValue(EventMessage event) {
        Set<AssociationValue> associations = associationValueResolver.extractAssociationValues(event);
        if (associations.isEmpty()) {
            return null;
        }
        return associations.iterator().next();
    }

    private boolean isAssignableClassIn(Class<?> aClass, Collection<Class<?>> classCollection) {
        for (Class<?> clazz : classCollection) {
            if (aClass != null && clazz.isAssignableFrom(aClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the types of Events that should cause the creation of a new Saga instance, even if one already exists.
     *
     * @param events the types of Events that should cause the creation of a new Saga instance, even if one already
     *               exists
     */
    public void setEventsToAlwaysCreateNewSagasFor(List<Class<?>> events) {
        this.eventsToAlwaysCreateNewSagasFor = events;
    }

    /**
     * Sets the types of Events that should cause the creation of a new Saga instance if one does not already exist.
     *
     * @param events the types of Events that should cause the creation of a new Saga instance if one does not already
     *               exist
     */
    public void setEventsToOptionallyCreateNewSagasFor(List<Class<?>> events) {
        this.eventsToOptionallyCreateNewSagasFor = events;
    }

    @Override
    public Class<?> getTargetType() {
        return sagaType;
    }
}

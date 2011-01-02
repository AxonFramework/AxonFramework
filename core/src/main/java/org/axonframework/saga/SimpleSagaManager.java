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

package org.axonframework.saga;

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple SagaManager implementation. This implementation requires the Event that should cause new Saga's to be created,
 * to be registered using {@link #setEventsToAlwaysCreateNewSagasFor(java.util.List)} and {@link
 * #setEventsToOptionallyCreateNewSagasFor(java.util.List)}.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SimpleSagaManager extends AbstractSagaManager {

    private final AssociationValueResolver associationValueResolver;

    private List<Class<? extends Event>> eventsToAlwaysCreateNewSagasFor = Collections.emptyList();
    private List<Class<? extends Event>> eventsToOptionallyCreateNewSagasFor = Collections.emptyList();
    private Class<? extends Saga> sagaType;

    /**
     * Initialize a SimpleSagaManager backed by the given resources.
     *
     * @param sagaType                 The type of Saga managed by this SagaManager
     * @param sagaRepository           The repository providing access to Saga instances
     * @param associationValueResolver The instance providing AssociationValues for incoming Events
     * @param sagaFactory              The factory creating new Saga instances
     * @param eventBus                 The event bus that the manager should register to
     */
    public SimpleSagaManager(Class<? extends Saga> sagaType, SagaRepository sagaRepository,
                             AssociationValueResolver associationValueResolver,
                             SagaFactory sagaFactory, EventBus eventBus) {
        super(eventBus, sagaRepository, sagaFactory);
        this.sagaType = sagaType;
        this.associationValueResolver = associationValueResolver;
    }

    /**
     * Initialize a SimpleSagaManager backed by the given resources, using a GenericSagaFactory.
     *
     * @param sagaType                 The type of Saga managed by this SagaManager
     * @param sagaRepository           The repository providing access to Saga instances
     * @param associationValueResolver The instance providing AssociationValues for incoming Events
     * @param eventBus                 The event bus that the manager should register to
     */
    public SimpleSagaManager(Class<? extends Saga> sagaType, SagaRepository sagaRepository,
                             AssociationValueResolver associationValueResolver,
                             EventBus eventBus) {
        super(eventBus, sagaRepository, new GenericSagaFactory());
        this.sagaType = sagaType;
        this.associationValueResolver = associationValueResolver;
    }

    @Override
    protected Set<Saga> findSagas(Event event) {
        Set<AssociationValue> associationValue = associationValueResolver.extractAssociationValue(event);
        Set<Saga> sagas = new HashSet<Saga>();
        sagas.addAll(getSagaRepository().find(sagaType, associationValue));
        if (sagas.isEmpty() && isAssignableClassIn(event.getClass(), eventsToOptionallyCreateNewSagasFor)
                || isAssignableClassIn(event.getClass(), eventsToAlwaysCreateNewSagasFor)) {
            Saga saga = createSaga(sagaType);
            sagas.add(saga);
            getSagaRepository().add(saga);
        }
        return sagas;
    }

    private boolean isAssignableClassIn(Class<? extends Event> aClass,
                                        Collection<Class<? extends Event>> classCollection) {
        for (Class clazz : classCollection) {
            if (clazz.isAssignableFrom(aClass)) {
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
    public void setEventsToAlwaysCreateNewSagasFor(List<Class<? extends Event>> events) {
        this.eventsToAlwaysCreateNewSagasFor = events;
    }

    /**
     * Sets the types of Events that should cause the creation of a new Saga instance if one does not already exist.
     *
     * @param events the types of Events that should cause the creation of a new Saga instance if one does not already
     *               exist
     */
    public void setEventsToOptionallyCreateNewSagasFor(List<Class<? extends Event>> events) {
        this.eventsToOptionallyCreateNewSagasFor = events;
    }
}

/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class SimpleSagaManager extends AbstractSagaManager {

    private final SagaRepository sagaRepository;
    private final SagaLookupPropertyResolver sagaLookupPropertyResolver;
    private final SagaFactory sagaFactory;

    private List<Class<? extends Event>> eventsToAlwaysCreateNewSagasFor = Collections.emptyList();
    private List<Class<? extends Event>> eventsToOptionallyCreateNewSagasFor = Collections.emptyList();
    private Class<? extends Saga> sagaType;

    public SimpleSagaManager(Class<? extends Saga> sagaType, SagaRepository sagaRepository,
                             SagaLookupPropertyResolver sagaLookupPropertyResolver,
                             SagaFactory sagaFactory, EventBus eventBus) {
        super(eventBus, sagaRepository);
        this.sagaType = sagaType;
        this.sagaRepository = sagaRepository;
        this.sagaLookupPropertyResolver = sagaLookupPropertyResolver;
        this.sagaFactory = sagaFactory;
    }

    @Override
    protected Set<Saga> findSagas(Event event) {
        SagaLookupProperty lookupProperty = sagaLookupPropertyResolver.extractLookupProperty(event);
        Set<Saga> sagas = new HashSet<Saga>();
        sagas.addAll(sagaRepository.find(sagaType, lookupProperty));
        if (sagas.isEmpty() && isAssignableClassIn(event.getClass(), eventsToOptionallyCreateNewSagasFor)) {
            sagas.add(sagaFactory.createSaga(sagaType));
        } else if (isAssignableClassIn(event.getClass(), eventsToAlwaysCreateNewSagasFor)) {
            sagas.add(sagaFactory.createSaga(sagaType));
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

    public void setEventsToAlwaysCreateNewSagasFor(List<Class<? extends Event>> events) {
        this.eventsToAlwaysCreateNewSagasFor = events;
    }

    public void setEventsToOptionallyCreateNewSagasFor(List<Class<? extends Event>> events) {
        this.eventsToOptionallyCreateNewSagasFor = events;
    }
}

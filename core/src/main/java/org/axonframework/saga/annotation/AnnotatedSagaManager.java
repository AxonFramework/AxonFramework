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

package org.axonframework.saga.annotation;

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.AbstractSagaManager;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaFactory;
import org.axonframework.saga.repository.SagaRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class AnnotatedSagaManager extends AbstractSagaManager {

    private Set<Class<? extends AbstractAnnotatedSaga>> managedSagaTypes = new HashSet<Class<? extends AbstractAnnotatedSaga>>();
    private SagaAnnotationInspector inspector = new SagaAnnotationInspector();
    private SagaRepository sagaRepository;
    private SagaFactory sagaFactory;

    public AnnotatedSagaManager(SagaRepository sagaRepository,
                                EventBus eventBus, Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        this(sagaRepository, new GenericSagaFactory(), eventBus, sagaClasses);
    }

    public AnnotatedSagaManager(SagaRepository sagaRepository,
                                SagaFactory sagaFactory, EventBus eventBus,
                                Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        super(eventBus, sagaRepository);
        this.sagaRepository = sagaRepository;
        this.sagaFactory = sagaFactory;
        managedSagaTypes.addAll(Arrays.asList(sagaClasses));
    }

    @Override
    protected Set<Saga> findSagas(Event event) {
        Set<Saga> sagasFound = new HashSet<Saga>();
        for (Class<? extends AbstractAnnotatedSaga> saga : managedSagaTypes) {
            sagasFound.addAll(findSagas(event, saga));
        }
        return sagasFound;
    }

    private <T extends AbstractAnnotatedSaga> Set<T> findSagas(Event event, Class<T> sagaType) {
        HandlerConfiguration configuration = inspector.findHandlerConfiguration(sagaType, event);
        if (!configuration.isHandlerAvailable()) {
            return Collections.emptySet();
        }
        Set<T> sagasFound = sagaRepository.find(sagaType, configuration.getAssociationValue());
        if ((sagasFound.isEmpty()
                && configuration.getCreationPolicy() == SagaCreationPolicy.IF_NONE_FOUND)
                || configuration.getCreationPolicy() == SagaCreationPolicy.ALWAYS) {
            T saga = sagaFactory.createSaga(sagaType);
            sagasFound.add(saga);
            saga.associateWith(configuration.getAssociationValue());
            sagaRepository.add(saga);
        }
        if (configuration.isDestructorHandler()) {
            for (AbstractAnnotatedSaga saga : sagasFound) {
                saga.end();
            }
        }

        return sagasFound;
    }
}

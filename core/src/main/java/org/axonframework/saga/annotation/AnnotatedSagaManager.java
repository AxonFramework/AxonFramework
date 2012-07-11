/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.saga.annotation;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.AbstractSagaManager;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaFactory;
import org.axonframework.saga.SagaRepository;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of the SagaManager that uses annotations on the Sagas to describe the lifecycle management. Unlike
 * the SimpleSagaManager, this implementation can manage several types of Saga in a single AnnotatedSagaManager.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AnnotatedSagaManager extends AbstractSagaManager {

    private final Set<SagaMethodMessageHandlerInspector<? extends AbstractAnnotatedSaga>> managedSagaTypes =
            new HashSet<SagaMethodMessageHandlerInspector<? extends AbstractAnnotatedSaga>>();

    /**
     * Initialize the AnnotatedSagaManager using the given resources, and using a <code>GenericSagaFactory</code>.
     *
     * @param sagaRepository The repository providing access to the Saga instances
     * @param eventBus       The event bus publishing the events
     * @param sagaClasses    The types of Saga that this instance should manage
     */
    public AnnotatedSagaManager(SagaRepository sagaRepository,
                                EventBus eventBus, Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        this(sagaRepository, new GenericSagaFactory(), eventBus, sagaClasses);
    }

    /**
     * Initialize the AnnotatedSagaManager using the given resources.
     *
     * @param sagaRepository The repository providing access to the Saga instances
     * @param sagaFactory    The factory creating new instances of a Saga
     * @param eventBus       The event bus publishing the events
     * @param sagaClasses    The types of Saga that this instance should manage
     */
    @SuppressWarnings({"unchecked"})
    public AnnotatedSagaManager(SagaRepository sagaRepository, SagaFactory sagaFactory, EventBus eventBus,
                                Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        super(eventBus, sagaRepository, sagaFactory);
        for (Class<? extends AbstractAnnotatedSaga> sagaClass : sagaClasses) {
            managedSagaTypes.add(SagaMethodMessageHandlerInspector.getInstance(sagaClass));
        }
    }

    @Override
    protected Set<Saga> findSagas(EventMessage event) {
        Set<Saga> sagasFound = new HashSet<Saga>();
        for (SagaMethodMessageHandlerInspector<? extends AbstractAnnotatedSaga> entry : managedSagaTypes) {
            sagasFound.addAll(findSagas(event, entry));
        }
        return sagasFound;
    }

    private <T extends AbstractAnnotatedSaga> Set<T> findSagas(EventMessage event,
                                                               SagaMethodMessageHandlerInspector<T> inspector) {
        SagaMethodMessageHandler configuration = inspector.getMessageHandler(event);
        if (!configuration.isHandlerAvailable()) {
            return Collections.emptySet();
        }
        AssociationValue associationValue = configuration.getAssociationValue(event);
        Set<T> sagasFound;
        if (associationValue == null) {
            sagasFound = Collections.emptySet();
        } else {
            sagasFound = getSagaRepository().find(inspector.getSagaType(), associationValue);
            if ((sagasFound.isEmpty()
                    && configuration.getCreationPolicy() == SagaCreationPolicy.IF_NONE_FOUND)
                    || configuration.getCreationPolicy() == SagaCreationPolicy.ALWAYS) {
                T saga = createSaga(inspector.getSagaType());
                sagasFound.add(saga);
                saga.associateWith(associationValue);
                getSagaRepository().add(saga);
            }
        }
        return sagasFound;
    }

    @Override
    public Class<?> getTargetType() {
        return managedSagaTypes.iterator().next().getSagaType();
    }
}

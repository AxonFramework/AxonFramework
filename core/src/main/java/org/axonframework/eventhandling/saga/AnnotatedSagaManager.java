/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.saga.metamodel.DefaultSagaMetaModelFactory;
import org.axonframework.eventhandling.saga.metamodel.SagaModel;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Implementation of the SagaManager that uses annotations on the Sagas to describe the lifecycle management. Unlike
 * the SimpleSagaManager, this implementation can manage several types of Saga in a single AnnotatedSagaManager.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AnnotatedSagaManager<T> extends AbstractSagaManager<T> {

    private final SagaModel<T> sagaMetaModel;

    /**
     * Initialize the AnnotatedSagaManager using given <code>repository</code> to load sagas and supporting given
     * annotated <code>sagaClasses</code>.
     *
     * @param sagaRepository The repository providing access to the Saga instances
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository,Callable<T> sagaFactory,
                                RollbackConfiguration rollbackConfiguration) {
        this(sagaType, sagaRepository, sagaFactory,
             new DefaultSagaMetaModelFactory().modelOf(sagaType), rollbackConfiguration);
    }

    /**
     * TODO: Javadoc
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, Callable<T> sagaFactory,
                                SagaModel<T> sagaMetaModel, RollbackConfiguration rollbackConfiguration) {
        super(sagaType, sagaRepository, sagaFactory, rollbackConfiguration);
        this.sagaMetaModel = sagaMetaModel;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected SagaInitializationPolicy getSagaCreationPolicy(EventMessage<?> event) {
        List<SagaMethodMessageHandler<T>> handlers = sagaMetaModel.findHandlerMethods(event);
        for (SagaMethodMessageHandler handler : handlers) {
            if (handler.getCreationPolicy() != SagaCreationPolicy.NONE) {
                return new SagaInitializationPolicy(handler.getCreationPolicy(), handler.getAssociationValue(event));
            }
        }
        return SagaInitializationPolicy.NONE;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Set<AssociationValue> extractAssociationValues(EventMessage<?> event) {
        List<SagaMethodMessageHandler<T>> handlers = sagaMetaModel.findHandlerMethods(event);
        return handlers.stream().map(handler -> handler.getAssociationValue(event)).collect(Collectors.toSet());
    }

    @Override
    protected boolean hasHandler(EventMessage<?> event) {
        return !sagaMetaModel.findHandlerMethods(event).isEmpty();
    }
}

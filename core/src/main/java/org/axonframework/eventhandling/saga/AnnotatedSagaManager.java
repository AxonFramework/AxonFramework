/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventhandling.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.saga.metamodel.DefaultSagaMetaModelFactory;
import org.axonframework.eventhandling.saga.metamodel.SagaModel;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of the SagaManager that uses annotations on the Sagas to describe the lifecycle management. Unlike the
 * SimpleSagaManager, this implementation can manage several types of Saga in a single AnnotatedSagaManager.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AnnotatedSagaManager<T> extends AbstractSagaManager<T> {

    private final SagaModel<T> sagaMetaModel;

    private static <T> T newInstance(Class<T> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new SagaInstantiationException("Exception while trying to instantiate a new Saga", e);
        }
    }

    /**
     * Initialize the AnnotatedSagaManager using given {@code repository} to load sagas. To create a new saga this
     * manager uses {@link #newInstance(Class)}. Uses a {@link DefaultSagaMetaModelFactory} for the saga's meta model.
     *
     * @param sagaType       the saga target type
     * @param sagaRepository The repository providing access to the Saga instances
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository) {
        this(sagaType, sagaRepository, () -> newInstance(sagaType));
    }

    /**
     * Initialize the AnnotatedSagaManager using given {@code repository} to load sagas. To create a new saga this
     * manager uses {@link #newInstance(Class)}. Uses a {@link DefaultSagaMetaModelFactory} for the saga's meta model.
     *
     * @param sagaType                 the saga target type
     * @param sagaRepository           The repository providing access to the Saga instances
     * @param parameterResolverFactory The ParameterResolverFactory instance to resolve parameter values for annotated
     *                                 handlers with
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, ParameterResolverFactory parameterResolverFactory) {
        this(sagaType, sagaRepository, () -> newInstance(sagaType), new DefaultSagaMetaModelFactory(parameterResolverFactory).modelOf(sagaType));
    }

    /**
     * Initialize the AnnotatedSagaManager using given {@code repository} to load sagas and {@code sagaFactory} to
     * create new sagas. Uses a {@link DefaultSagaMetaModelFactory} for the saga's meta model.
     *
     * @param sagaType       the saga target type
     * @param sagaRepository The repository providing access to the Saga instances
     * @param sagaFactory    the factory for new saga instances of type {@code T}
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, Supplier<T> sagaFactory) {
        this(sagaType, sagaRepository, sagaFactory, new DefaultSagaMetaModelFactory().modelOf(sagaType));
    }

    /**
     * Initialize the AnnotatedSagaManager using given {@code repository} to load sagas, the {@code sagaFactory} to
     * create new sagas and the {@code sagaMetaModel} to delegate messages to the saga instances.
     *
     * @param sagaType       the saga target type
     * @param sagaRepository The repository providing access to the Saga instances
     * @param sagaFactory    the factory for new saga instances of type {@code T}
     * @param sagaMetaModel  the meta model to delegate messages to a saga instance
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, Supplier<T> sagaFactory,
                                SagaModel<T> sagaMetaModel) {
        super(sagaType, sagaRepository, sagaFactory);
        this.sagaMetaModel = sagaMetaModel;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected SagaInitializationPolicy getSagaCreationPolicy(EventMessage<?> event) {
        List<SagaMethodMessageHandlingMember<T>> handlers = sagaMetaModel.findHandlerMethods(event);
        for (SagaMethodMessageHandlingMember handler : handlers) {
            if (handler.getCreationPolicy() != SagaCreationPolicy.NONE) {
                return new SagaInitializationPolicy(handler.getCreationPolicy(), handler.getAssociationValue(event));
            }
        }
        return SagaInitializationPolicy.NONE;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Set<AssociationValue> extractAssociationValues(EventMessage<?> event) {
        List<SagaMethodMessageHandlingMember<T>> handlers = sagaMetaModel.findHandlerMethods(event);
        return handlers.stream().map(handler -> handler.getAssociationValue(event)).collect(Collectors.toSet());
    }

    @Override
    public boolean hasHandler(EventMessage<?> event) {
        return !sagaMetaModel.findHandlerMethods(event).isEmpty();
    }
}

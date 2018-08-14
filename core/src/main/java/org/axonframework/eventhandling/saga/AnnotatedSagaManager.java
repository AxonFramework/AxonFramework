/*
 * Copyright (c) 2010-2017. Axon Framework
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
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.saga.metamodel.AnnotationSagaMetaModelFactory;
import org.axonframework.eventhandling.saga.metamodel.SagaModel;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of the SagaManager that uses annotations on the Sagas to describe the lifecycle management. Unlike the
 * SimpleSagaManager, this implementation can manage several types of Saga in a single AnnotatedSagaManager.
 *
 * @param <T> The type of Saga managed by this instance
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
     * manager uses {@link #newInstance(Class)}. Uses a {@link AnnotationSagaMetaModelFactory} for the saga's meta
     * model.
     *
     * @param sagaType       the saga target type
     * @param sagaRepository The repository providing access to the Saga instances
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository) {
        this(sagaType, sagaRepository, () -> newInstance(sagaType));
    }

    /**
     * Initialize the AnnotatedSagaManager using given {@code repository} to load sagas. To create a new saga this
     * manager uses {@link #newInstance(Class)}. Uses a {@link AnnotationSagaMetaModelFactory} for the saga's meta
     * model.
     *
     * @param sagaType                       The saga target type
     * @param sagaRepository                 The repository providing access to the Saga instances
     * @param parameterResolverFactory       The ParameterResolverFactory instance to resolve parameter values for
     *                                       annotated handlers with
     * @param listenerInvocationErrorHandler The error handler to invoke when an error occurs
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository,
                                ParameterResolverFactory parameterResolverFactory,
                                ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
        this(sagaType,
             sagaRepository,
             () -> newInstance(sagaType),
             new AnnotationSagaMetaModelFactory(parameterResolverFactory).modelOf(sagaType),
             listenerInvocationErrorHandler);
    }

    /**
     * Initialize the AnnotatedSagaManager using given {@code repository} to load sagas. To create a new saga this
     * manager uses {@link #newInstance(Class)}. Uses a {@link AnnotationSagaMetaModelFactory} for the saga's meta
     * model.
     *
     * @param sagaType                       The saga target type
     * @param sagaRepository                 The repository providing access to the Saga instances
     * @param parameterResolverFactory       The ParameterResolverFactory instance to resolve parameter values for
     *                                       annotated handlers with
     * @param handlerDefinition              The handler definition used to create concrete handlers
     * @param listenerInvocationErrorHandler The error handler to invoke when an error occurs
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository,
                                ParameterResolverFactory parameterResolverFactory,
                                HandlerDefinition handlerDefinition,
                                ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
        this(sagaType,
             sagaRepository,
             () -> newInstance(sagaType),
             new AnnotationSagaMetaModelFactory(parameterResolverFactory, handlerDefinition).modelOf(sagaType),
             listenerInvocationErrorHandler);
    }

    /**
     * Initialize the AnnotatedSagaManager using given {@code repository} to load sagas and {@code sagaFactory} to
     * create new sagas. Uses a {@link AnnotationSagaMetaModelFactory} for the saga's meta model.
     *
     * @param sagaType       The saga target type
     * @param sagaRepository The repository providing access to the Saga instances
     * @param sagaFactory    The factory for new saga instances of type {@code T}
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, Supplier<T> sagaFactory) {
        this(sagaType,
             sagaRepository,
             sagaFactory,
             new AnnotationSagaMetaModelFactory().modelOf(sagaType),
             new LoggingErrorHandler());
    }

    /**
     * Initialize the AnnotatedSagaManager using given {@code repository} to load sagas, the {@code sagaFactory} to
     * create new sagas and the {@code sagaMetaModel} to delegate messages to the saga instances.
     *
     * @param sagaType                       The saga target type
     * @param sagaRepository                 The repository providing access to the Saga instances
     * @param sagaFactory                    The factory for new saga instances of type {@code T}
     * @param sagaMetaModel                  The meta model to delegate messages to a saga instance
     * @param listenerInvocationErrorHandler The error handler to invoke when an error occurs
     */
    public AnnotatedSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, Supplier<T> sagaFactory,
                                SagaModel<T> sagaMetaModel,
                                ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
        super(sagaType, sagaRepository, sagaFactory, listenerInvocationErrorHandler);
        this.sagaMetaModel = sagaMetaModel;
    }

    @Override
    public boolean canHandle(EventMessage<?> eventMessage, Segment segment) {
        // The segment is used to filter Saga instances, so all events match when there's a handler
        return sagaMetaModel.hasHandlerMethod(eventMessage);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected SagaInitializationPolicy getSagaCreationPolicy(EventMessage<?> event) {
        return sagaMetaModel.findHandlerMethods(event).stream()
                            .map(h -> h.unwrap(SagaMethodMessageHandlingMember.class).orElse(null))
                            .filter(Objects::nonNull)
                            .filter(sh -> sh.getCreationPolicy() != SagaCreationPolicy.NONE)
                            .map(sh -> new SagaInitializationPolicy(
                                    sh.getCreationPolicy(), sh.getAssociationValue(event)
                            ))
                            .findFirst()
                            .orElse(SagaInitializationPolicy.NONE);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Set<AssociationValue> extractAssociationValues(EventMessage<?> event) {
        return sagaMetaModel.findHandlerMethods(event).stream()
                            .map(h -> h.unwrap(SagaMethodMessageHandlingMember.class).orElse(null))
                            .filter(Objects::nonNull)
                            .map(h -> h.getAssociationValue(event))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
    }
}

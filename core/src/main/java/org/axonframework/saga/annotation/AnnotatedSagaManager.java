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

package org.axonframework.saga.annotation;

import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.AbstractSagaManager;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.GenericSagaFactory;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaCreationPolicy;
import org.axonframework.saga.SagaFactory;
import org.axonframework.saga.SagaInitializationPolicy;
import org.axonframework.saga.SagaRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of the SagaManager that uses annotations on the Sagas to describe the lifecycle management. Unlike
 * the SimpleSagaManager, this implementation can manage several types of Saga in a single AnnotatedSagaManager.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class AnnotatedSagaManager extends AbstractSagaManager {

    private final ParameterResolverFactory parameterResolverFactory;

    /**
     * Initialize the AnnotatedSagaManager using the given resources, and using a <code>GenericSagaFactory</code>.
     *
     * @param sagaRepository The repository providing access to the Saga instances
     * @param eventBus       The event bus publishing the events
     * @param sagaClasses    The types of Saga that this instance should manage
     * @deprecated use {@link #AnnotatedSagaManager(org.axonframework.saga.SagaRepository,
     * Class[])} instead and register this instance using {@link
     * EventBus#subscribe(org.axonframework.eventhandling.EventListener)}
     */
    @Deprecated
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
     * @deprecated use {@link #AnnotatedSagaManager(org.axonframework.saga.SagaRepository,
     * org.axonframework.saga.SagaFactory, Class[])} instead and register this instance using {@link
     * EventBus#subscribe(org.axonframework.eventhandling.EventListener)}
     */
    @Deprecated
    public AnnotatedSagaManager(SagaRepository sagaRepository, SagaFactory sagaFactory, EventBus eventBus,
                                Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        super(eventBus, sagaRepository, sagaFactory, sagaClasses);
        this.parameterResolverFactory = ClasspathParameterResolverFactory.forClass(sagaClasses[0]);
    }

    /**
     * Initialize the AnnotatedSagaManager using given <code>repository</code> to load sagas and supporting given
     * annotated <code>sagaClasses</code>.
     *
     * @param sagaRepository The repository providing access to the Saga instances
     * @param sagaClasses    The types of Saga that this instance should manage
     */
    public AnnotatedSagaManager(SagaRepository sagaRepository,
                                Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        this(sagaRepository,
             ClasspathParameterResolverFactory.forClass(sagaClasses.length > 0 ? sagaClasses[0] : sagaRepository.getClass()),
             sagaClasses);
    }

    /**
     * Initialize the AnnotatedSagaManager using given <code>repository</code> to load sagas and supporting given
     * annotated <code>sagaClasses</code>.
     *
     * @param sagaRepository           The repository providing access to the Saga instances
     * @param parameterResolverFactory The parameterResolverFactory to resolve parameters with for the saga instance's
     *                                 handler methods
     * @param sagaClasses              The types of Saga that this instance should manage
     */
    public AnnotatedSagaManager(SagaRepository sagaRepository, ParameterResolverFactory parameterResolverFactory,
                                Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        this(sagaRepository, new GenericSagaFactory(), parameterResolverFactory, sagaClasses);
    }

    /**
     * Initialize the AnnotatedSagaManager using the given resources.
     *
     * @param sagaRepository The repository providing access to the Saga instances
     * @param sagaFactory    The factory creating new instances of a Saga
     * @param sagaClasses    The types of Saga that this instance should manage
     */
    public AnnotatedSagaManager(SagaRepository sagaRepository, SagaFactory sagaFactory,
                                Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        this(sagaRepository, sagaFactory,
             ClasspathParameterResolverFactory.forClass(sagaClasses.length > 0 ? sagaClasses[0] : sagaRepository.getClass()),
             sagaClasses);
    }

    /**
     * Initialize the AnnotatedSagaManager using the given resources.
     *
     * @param sagaRepository           The repository providing access to the Saga instances
     * @param sagaFactory              The factory creating new instances of a Saga
     * @param parameterResolverFactory The parameterResolverFactory to resolve parameters with for the saga instance's
     *                                 handler methods
     * @param sagaClasses              The types of Saga that this instance should manage
     */
    public AnnotatedSagaManager(SagaRepository sagaRepository, SagaFactory sagaFactory,
                                ParameterResolverFactory parameterResolverFactory,
                                Class<? extends AbstractAnnotatedSaga>... sagaClasses) {
        super(sagaRepository, sagaFactory, sagaClasses);
        this.parameterResolverFactory = parameterResolverFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected SagaInitializationPolicy getSagaCreationPolicy(Class<? extends Saga> sagaType, EventMessage event) {
        SagaMethodMessageHandlerInspector<? extends AbstractAnnotatedSaga> inspector =
                SagaMethodMessageHandlerInspector.getInstance((Class<? extends AbstractAnnotatedSaga>) sagaType,
                                                              parameterResolverFactory);
        final List<SagaMethodMessageHandler> handlers = inspector.getMessageHandlers(event);
        for (SagaMethodMessageHandler handler : handlers) {
            if (handler.getCreationPolicy() != SagaCreationPolicy.NONE) {
                return new SagaInitializationPolicy(handler.getCreationPolicy(), handler.getAssociationValue(event));
            }
        }
        return SagaInitializationPolicy.NONE;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Set<AssociationValue> extractAssociationValues(Class<? extends Saga> sagaType,
                                                                    EventMessage event) {
        SagaMethodMessageHandlerInspector<? extends AbstractAnnotatedSaga> inspector =
                SagaMethodMessageHandlerInspector.getInstance((Class<? extends AbstractAnnotatedSaga>) sagaType,
                                                              parameterResolverFactory);
        final List<SagaMethodMessageHandler> handlers = inspector.getMessageHandlers(event);
        Set<AssociationValue> values = new HashSet<AssociationValue>(handlers.size());
        for (SagaMethodMessageHandler handler : handlers) {
            values.add(handler.getAssociationValue(event));
        }
        return values;
    }

    @Override
    protected void preProcessSaga(Saga saga) {
        if (parameterResolverFactory != null) {
            ((AbstractAnnotatedSaga) saga).registerParameterResolverFactory(parameterResolverFactory);
        }
    }

    @Override
    public Class<?> getTargetType() {
        if (getManagedSagaTypes().isEmpty()) {
            return Void.TYPE;
        }
        return getManagedSagaTypes().iterator().next();
    }
}

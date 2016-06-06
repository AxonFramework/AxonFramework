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

import org.axonframework.common.Assert;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Abstract implementation of the SagaManager interface that provides basic functionality required by most SagaManager
 * implementations. Provides support for Saga lifecycle management and asynchronous handling of events.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSagaManager<T> implements EventProcessor, MessageHandler<EventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSagaManager.class);

    private final SagaRepository<T> sagaRepository;
    private final Class<T> sagaType;
    private volatile boolean suppressExceptions = true;
    private final List<MessageHandlerInterceptor<EventMessage<?>>> interceptors = new CopyOnWriteArrayList<>();
    private final Callable<T> sagaFactory;
    private final RollbackConfiguration rollbackConfiguration;

    /**
     * Initializes the SagaManager with the given {@code sagaRepository}.
     *
     * @param sagaType              The type of Saga Managed by this instance
     * @param sagaRepository        The repository providing the saga instances.
     * @param sagaFactory           The factory responsible for creating new Saga instances
     * @param rollbackConfiguration The configuration of the exceptions that lead to a Unit of Work rollback
     */
    protected AbstractSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, Callable<T> sagaFactory,
                                  RollbackConfiguration rollbackConfiguration) {
        this.sagaType = sagaType;
        this.sagaFactory = sagaFactory;
        this.rollbackConfiguration = rollbackConfiguration;
        Assert.notNull(sagaRepository, "sagaRepository may not be null");
        this.sagaRepository = sagaRepository;
    }

    @Override
    public String getName() {
        return "SagaManager[" + sagaType.getName() + "]";
    }

    @Override
    public Registration registerInterceptor(MessageHandlerInterceptor<EventMessage<?>> interceptor) {
        if (!interceptors.contains(interceptor)) {
            interceptors.add(interceptor);
        }
        return () -> interceptors.remove(interceptor);
    }

    @Override
    public Object handle(EventMessage<?> event, UnitOfWork<? extends EventMessage<?>> unitOfWork) throws Exception {
        if (interceptors.isEmpty()) {
            return doHandle(event, unitOfWork);
        } else {
            return new DefaultInterceptorChain<>(unitOfWork, interceptors,
                                                 (MessageHandler<EventMessage<?>>) this::doHandle).proceed();
        }
    }

    protected Object doHandle(EventMessage<?> event,
                              UnitOfWork<? extends EventMessage<?>> unitOfWork) throws Exception {
        Set<AssociationValue> associationValues = extractAssociationValues(event);
        Set<Saga<T>> sagas =
                associationValues.stream().flatMap(associationValue -> sagaRepository.find(associationValue).stream())
                        .map(sagaRepository::load).filter(s -> s != null).filter(Saga::isActive)
                        .collect(Collectors.toCollection(HashSet<Saga<T>>::new));
        boolean sagaOfTypeInvoked = false;
        for (Saga<T> saga : sagas) {
            if (doInvokeSaga(event, saga)) {
                sagaOfTypeInvoked = true;
            }
        }
        SagaInitializationPolicy initializationPolicy = getSagaCreationPolicy(event);
        if (initializationPolicy.getCreationPolicy() == SagaCreationPolicy.ALWAYS ||
                (!sagaOfTypeInvoked && initializationPolicy.getCreationPolicy() == SagaCreationPolicy.IF_NONE_FOUND)) {
            startNewSaga(event, initializationPolicy.getInitialAssociationValue());
        }
        return null;
    }

    @Override
    public void accept(List<? extends EventMessage<?>> events) {
        for (EventMessage<?> sourceEvent : events) {
            if (hasHandler(sourceEvent)) {
                DefaultUnitOfWork<? extends EventMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(sourceEvent);
                try {
                    unitOfWork.executeWithResult(() -> handle(sourceEvent, unitOfWork), rollbackConfiguration);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SagaExecutionException("Exception while processing an event in a Saga", e);
                }
            }
        }
    }

    private void startNewSaga(EventMessage event, AssociationValue associationValue) throws Exception {
        Saga<T> newSaga = sagaRepository.newInstance(sagaFactory);
        newSaga.getAssociationValues().add(associationValue);
        doInvokeSaga(event, newSaga);
    }

    /**
     * Returns the Saga Initialization Policy for a Saga of the given <code>sagaType</code> and <code>event</code>. This
     * policy provides the conditions to create new Saga instance, as well as the initial association of that saga.
     *
     * @param event The Event that is being dispatched to Saga instances
     * @return the initialization policy for the Saga
     */
    protected abstract SagaInitializationPolicy getSagaCreationPolicy(EventMessage<?> event);

    /**
     * Extracts the AssociationValues from the given <code>event</code> as relevant for a Saga of given
     * <code>sagaType</code>. A single event may be associated with multiple values.
     *
     * @param event The event containing the association information
     * @return the AssociationValues indicating which Sagas should handle given event
     */
    protected abstract Set<AssociationValue> extractAssociationValues(EventMessage<?> event);

    protected abstract boolean hasHandler(EventMessage<?> event);

    private boolean doInvokeSaga(EventMessage event, Saga<T> saga) {
        try {
            return saga.handle(event);
        } catch (Exception e) {
            if (suppressExceptions) {
                logger.error(format("An exception occurred while a Saga [%s] was handling an Event [%s]:",
                                    saga.getClass().getSimpleName(), event.getPayloadType().getSimpleName()), e);
                return true;
            } else {
                throw e;
            }
        }
    }

    /**
     * Sets whether or not to suppress any exceptions that are cause by invoking Sagas. When suppressed, exceptions are
     * logged. Defaults to <code>true</code>.
     *
     * @param suppressExceptions whether or not to suppress exceptions from Sagas.
     */
    public void setSuppressExceptions(boolean suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }

}

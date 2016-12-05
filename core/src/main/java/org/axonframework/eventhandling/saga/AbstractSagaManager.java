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
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Abstract implementation of the SagaManager interface that provides basic functionality required by most SagaManager
 * implementations. Provides support for Saga lifecycle management and asynchronous handling of events.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSagaManager<T> implements EventHandlerInvoker {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSagaManager.class);

    private final SagaRepository<T> sagaRepository;
    private final Class<T> sagaType;
    private volatile boolean suppressExceptions = true;
    private final Supplier<T> sagaFactory;

    /**
     * Initializes the SagaManager with the given {@code sagaRepository}.
     *
     * @param sagaType              The type of Saga Managed by this instance
     * @param sagaRepository        The repository providing the saga instances.
     * @param sagaFactory           The factory responsible for creating new Saga instances
     */
    protected AbstractSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, Supplier<T> sagaFactory) {
        this.sagaType = sagaType;
        this.sagaFactory = sagaFactory;
        Assert.notNull(sagaRepository, () -> "sagaRepository may not be null");
        this.sagaRepository = sagaRepository;
    }

    @Override
    public Object handle(EventMessage<?> event) throws Exception {
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

    private void startNewSaga(EventMessage event, AssociationValue associationValue) {
        Saga<T> newSaga = sagaRepository.createInstance(IdentifierFactory.getInstance().generateIdentifier(), sagaFactory);
        newSaga.getAssociationValues().add(associationValue);
        doInvokeSaga(event, newSaga);
    }

    /**
     * Returns the Saga Initialization Policy for a Saga of the given {@code sagaType} and {@code event}. This
     * policy provides the conditions to create new Saga instance, as well as the initial association of that saga.
     *
     * @param event The Event that is being dispatched to Saga instances
     * @return the initialization policy for the Saga
     */
    protected abstract SagaInitializationPolicy getSagaCreationPolicy(EventMessage<?> event);

    /**
     * Extracts the AssociationValues from the given {@code event} as relevant for a Saga of given
     * {@code sagaType}. A single event may be associated with multiple values.
     *
     * @param event The event containing the association information
     * @return the AssociationValues indicating which Sagas should handle given event
     */
    protected abstract Set<AssociationValue> extractAssociationValues(EventMessage<?> event);

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
     * logged. Defaults to {@code true}.
     *
     * @param suppressExceptions whether or not to suppress exceptions from Sagas.
     */
    public void setSuppressExceptions(boolean suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }

    /**
     * Returns the class of Saga managed by this SagaManager
     *
     * @return the managed saga type
     */
    public Class<T> getSagaType() {
        return sagaType;
    }
}

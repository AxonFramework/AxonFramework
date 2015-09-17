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

package org.axonframework.saga;

import org.axonframework.common.Assert;
import org.axonframework.common.lock.IdentifierBasedLock;
import org.axonframework.correlation.CorrelationDataHolder;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import static java.lang.String.format;

/**
 * Abstract implementation of the SagaManager interface that provides basic functionality required by most
 * synchronous SagaManager implementations.
 * Provides support for Saga lifecycle management and asynchronous handling of events.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSynchronousSagaManager extends AbstractSagaManager<Saga> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSynchronousSagaManager.class);

    private final SagaRepository sagaRepository;
    private final SagaFactory sagaFactory;
    private final IdentifierBasedLock lock = new IdentifierBasedLock();
    private final Map<String, Saga> sagasInCreation = new ConcurrentHashMap<String, Saga>();
    private volatile boolean suppressExceptions = true;
    private volatile boolean synchronizeSagaAccess = true;

    /**
     * Initializes the SagaManager with the given <code>eventBus</code> and <code>sagaRepository</code>.
     *
     * @param eventBus       The event bus providing the events to route to sagas.
     * @param sagaRepository The repository providing the saga instances.
     * @param sagaFactory    The factory providing new saga instances
     * @param sagaTypes      The types of Saga supported by this Saga Manager
     * @deprecated use {@link #AbstractSynchronousSagaManager(org.axonframework.saga.SagaRepository, org.axonframework.saga.SagaFactory, Class[])} and register using {@link
     * org.axonframework.eventhandling.EventBus#subscribe(org.axonframework.eventhandling.EventListener)}
     */
    @Deprecated
    public AbstractSynchronousSagaManager(EventBus eventBus, SagaRepository sagaRepository, SagaFactory sagaFactory,
                                          Class<? extends Saga>... sagaTypes) {
        super(eventBus, sagaTypes);
        Assert.notNull(eventBus, "eventBus may not be null");
        Assert.notNull(sagaRepository, "sagaRepository may not be null");
        Assert.notNull(sagaFactory, "sagaFactory may not be null");
        this.sagaRepository = sagaRepository;
        this.sagaFactory = sagaFactory;
    }

    /**
     * Initializes the SagaManager with the given <code>sagaRepository</code>.
     *
     * @param sagaRepository The repository providing the saga instances.
     * @param sagaFactory    The factory providing new saga instances
     * @param sagaTypes      The types of Saga supported by this Saga Manager
     */
    public AbstractSynchronousSagaManager(SagaRepository sagaRepository, SagaFactory sagaFactory,
                                          Class<? extends Saga>... sagaTypes) {
        super(null, sagaTypes);
        Assert.notNull(sagaRepository, "sagaRepository may not be null");
        Assert.notNull(sagaFactory, "sagaFactory may not be null");
        this.sagaRepository = sagaRepository;
        this.sagaFactory = sagaFactory;
    }

    @Override
    public void handle(final EventMessage event) {
        for (Class<? extends Saga> sagaType : sagaTypes) {
            Collection<AssociationValue> associationValues = extractAssociationValues(sagaType, event);
            if (associationValues != null && !associationValues.isEmpty()) {
                boolean sagaOfTypeInvoked = invokeExistingSagas(event, sagaType, associationValues);
                SagaInitializationPolicy initializationPolicy = getSagaCreationPolicy(sagaType, event);
                if (initializationPolicy.getCreationPolicy() == SagaCreationPolicy.ALWAYS
                        || (!sagaOfTypeInvoked
                        && initializationPolicy.getCreationPolicy() == SagaCreationPolicy.IF_NONE_FOUND)) {
                    startNewSaga(event, sagaType, initializationPolicy.getInitialAssociationValue());
                }
            }
        }
    }

    private boolean invokeExistingSagas(EventMessage event, Class<? extends Saga> sagaType,
                                        Collection<AssociationValue> associationValues) {
        Set<String> sagas = new TreeSet<String>();
        for (AssociationValue associationValue : associationValues) {
            sagas.addAll(sagaRepository.find(sagaType, associationValue));
        }
        for (Saga sagaInCreation : sagasInCreation.values()) {
            if (sagaType.isInstance(sagaInCreation)
                    && containsAny(sagaInCreation.getAssociationValues(), associationValues)) {
                sagas.add(sagaInCreation.getSagaIdentifier());
            }
        }
        boolean sagaOfTypeInvoked = false;
        for (final String sagaId : sagas) {
            if (synchronizeSagaAccess) {
                lock.obtainLock(sagaId);
                Saga invokedSaga = null;
                try {
                    invokedSaga = loadAndInvoke(event, sagaId, associationValues);
                    if (invokedSaga != null) {
                        sagaOfTypeInvoked = true;
                    }
                } finally {
                    doReleaseLock(sagaId, invokedSaga);
                }
            } else {
                loadAndInvoke(event, sagaId, associationValues);
            }
        }
        return sagaOfTypeInvoked;
    }

    private boolean containsAny(AssociationValues associationValues, Collection<AssociationValue> toFind) {
        for (AssociationValue valueToFind : toFind) {
            if (associationValues.contains(valueToFind)) {
                return true;
            }
        }
        return false;
    }

    private void startNewSaga(EventMessage event, Class<? extends Saga> sagaType, AssociationValue associationValue) {
        Saga newSaga = sagaFactory.createSaga(sagaType);
        newSaga.getAssociationValues().add(associationValue);
        preProcessSaga(newSaga);
        sagasInCreation.put(newSaga.getSagaIdentifier(), newSaga);
        try {
            if (synchronizeSagaAccess) {
                lock.obtainLock(newSaga.getSagaIdentifier());
                try {
                    doInvokeSaga(event, newSaga);
                } finally {
                    try {
                        sagaRepository.add(newSaga);
                    } finally {
                        doReleaseLock(newSaga.getSagaIdentifier(), newSaga);
                    }
                }
            } else {
                try {
                    doInvokeSaga(event, newSaga);
                } finally {
                    sagaRepository.add(newSaga);
                }
            }
        } finally {
            removeEntry(newSaga.getSagaIdentifier(), sagasInCreation);
        }
    }

    private void doReleaseLock(final String sagaId, final Saga sagaInstance) {
        if (sagaInstance == null || !CurrentUnitOfWork.isStarted()) {
            lock.releaseLock(sagaId);
        } else if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().registerListener(new UnitOfWorkListenerAdapter() {
                @Override
                public void onCleanup(UnitOfWork unitOfWork) {
                    // a reference to the saga is maintained to prevent it from GC until after the UoW commit
                    lock.releaseLock(sagaInstance.getSagaIdentifier());
                }
            });
        }
    }

    private void removeEntry(final String sagaIdentifier, final Map<String, ?> sagaMap) {
        if (!CurrentUnitOfWork.isStarted()) {
            sagaMap.remove(sagaIdentifier);
        } else {
            CurrentUnitOfWork.get().registerListener(new UnitOfWorkListenerAdapter() {
                @Override
                public void afterCommit(UnitOfWork unitOfWork) {
                    sagaMap.remove(sagaIdentifier);
                }
            });
        }
    }

    /**
     * Returns the Saga Initialization Policy for a Saga of the given <code>sagaType</code> and <code>event</code>.
     * This policy provides the conditions to create new Saga instance, as well as the initial association of that
     * saga.
     *
     * @param sagaType The type of Saga to get the creation policy for
     * @param event    The Event that is being dispatched to Saga instances
     * @return the initialization policy for the Saga
     */
    protected abstract SagaInitializationPolicy getSagaCreationPolicy(Class<? extends Saga> sagaType,
                                                                      EventMessage event);

    /**
     * Extracts the AssociationValues from the given <code>event</code> as relevant for a Saga of given
     * <code>sagaType</code>. A single event may be associated with multiple values.
     *
     * @param sagaType The type of Saga about to handle the Event
     * @param event    The event containing the association information
     * @return the AssociationValues indicating which Sagas should handle given event
     */
    protected abstract Set<AssociationValue> extractAssociationValues(Class<? extends Saga> sagaType,
                                                                      EventMessage event);

    private Saga loadAndInvoke(EventMessage event, String sagaId, Collection<AssociationValue> associations) {
        Saga saga = sagasInCreation.get(sagaId);
        if (saga == null) {
            saga = sagaRepository.load(sagaId);
        }

        if (saga == null || !saga.isActive() || !containsAny(saga.getAssociationValues(), associations)) {
            return null;
        }
        preProcessSaga(saga);
        try {
            doInvokeSaga(event, saga);
        } finally {
            commit(saga);
        }
        return saga;
    }

    /**
     * Perform pre-processing of sagas that have been newly created or have been loaded from the repository. This
     * method is invoked prior to invocation of the saga instance itself.
     *
     * @param saga The saga instance for pre-processing
     */
    protected void preProcessSaga(Saga saga) {
    }

    private void doInvokeSaga(EventMessage event, Saga saga) {
        try {
            CorrelationDataHolder.setCorrelationData(correlationDataProvider.correlationDataFor(event));
            saga.handle(event);
        } catch (RuntimeException e) {
            if (suppressExceptions) {
                logger.error(format("An exception occurred while a Saga [%s] was handling an Event [%s]:",
                                    saga.getClass().getSimpleName(),
                                    event.getPayloadType().getSimpleName()),
                             e);
            } else {
                throw e;
            }
        } finally {
            CorrelationDataHolder.clear();
        }
    }

    /**
     * Commits the given <code>saga</code> to the registered repository.
     *
     * @param saga the Saga to commit.
     */
    protected void commit(Saga saga) {
        sagaRepository.commit(saga);
    }

    /**
     * Unsubscribe the EventListener with the configured EventBus.
     *
     * @deprecated Use {@link org.axonframework.eventhandling.EventBus#unsubscribe(org.axonframework.eventhandling.EventListener)} to unsubscribe this
     * instance
     */
    @Override
    @PreDestroy
    @Deprecated
    public void unsubscribe() {
        if (eventBus != null) {
            eventBus.unsubscribe(this);
        }
    }

    /**
     * Subscribe the EventListener with the configured EventBus.
     *
     * @deprecated Use {@link org.axonframework.eventhandling.EventBus#subscribe(org.axonframework.eventhandling.EventListener)} to subscribe this
     * instance
     */
    @Override
    @PostConstruct
    @Deprecated
    public void subscribe() {
        if (eventBus != null) {
            eventBus.subscribe(this);
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

    /**
     * Sets whether of not access to Saga's Event Handler should by synchronized. Defaults to <code>true</code>. Sets
     * to <code>false</code> only if the Saga managed by this manager are completely thread safe by themselves.
     *
     * @param synchronizeSagaAccess whether or not to synchronize access to Saga's event handlers.
     */
    public void setSynchronizeSagaAccess(boolean synchronizeSagaAccess) {
        this.synchronizeSagaAccess = synchronizeSagaAccess;
    }
}

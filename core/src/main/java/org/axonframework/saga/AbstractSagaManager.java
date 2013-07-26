/*
 * Copyright (c) 2010-2012. Axon Framework
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
import org.axonframework.common.Subscribable;
import org.axonframework.common.lock.IdentifierBasedLock;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import static java.lang.String.format;

/**
 * Abstract implementation of the SagaManager interface that provides basic functionality required by most SagaManager
 * implementations. Provides support for Saga lifecycle management and asynchronous handling of events.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSagaManager implements SagaManager, Subscribable {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSagaManager.class);

    private final EventBus eventBus;
    private final SagaRepository sagaRepository;
    private SagaFactory sagaFactory;
    private final Class<? extends Saga>[] sagaTypes;
    private volatile boolean suppressExceptions = true;
    private volatile boolean synchronizeSagaAccess = true;
    private final IdentifierBasedLock lock = new IdentifierBasedLock();

    /**
     * Initializes the SagaManager with the given <code>eventBus</code> and <code>sagaRepository</code>.
     *
     * @param eventBus       The event bus providing the events to route to sagas.
     * @param sagaRepository The repository providing the saga instances.
     * @param sagaFactory    The factory providing new saga instances
     * @param sagaTypes      The types of Saga supported by this Saga Manager
     * @deprecated use {@link #AbstractSagaManager(SagaRepository, SagaFactory, Class[])} and register using {@link
     *             EventBus#subscribe(org.axonframework.eventhandling.EventListener)}
     */
    @Deprecated
    public AbstractSagaManager(EventBus eventBus, SagaRepository sagaRepository, SagaFactory sagaFactory,
                               Class<? extends Saga>... sagaTypes) {
        Assert.notNull(eventBus, "eventBus may not be null");
        Assert.notNull(sagaRepository, "sagaRepository may not be null");
        Assert.notNull(sagaFactory, "sagaFactory may not be null");
        this.eventBus = eventBus;
        this.sagaRepository = sagaRepository;
        this.sagaFactory = sagaFactory;
        this.sagaTypes = sagaTypes;
    }

    /**
     * Initializes the SagaManager with the given <code>sagaRepository</code>.
     *
     * @param sagaRepository The repository providing the saga instances.
     * @param sagaFactory    The factory providing new saga instances
     * @param sagaTypes      The types of Saga supported by this Saga Manager
     */
    public AbstractSagaManager(SagaRepository sagaRepository, SagaFactory sagaFactory,
                               Class<? extends Saga>... sagaTypes) {
        Assert.notNull(sagaRepository, "sagaRepository may not be null");
        Assert.notNull(sagaFactory, "sagaFactory may not be null");
        this.eventBus = null;
        this.sagaRepository = sagaRepository;
        this.sagaFactory = sagaFactory;
        this.sagaTypes = sagaTypes;
    }

    @Override
    public void handle(final EventMessage event) {
        for (Class<? extends Saga> sagaType : sagaTypes) {
            AssociationValue associationValue = extractAssociationValue(sagaType, event);
            if (associationValue != null) {
                boolean sagaOfTypeInvoked = invokeExistingSagas(event, sagaType, associationValue);
                SagaCreationPolicy creationPolicy = getSagaCreationPolicy(sagaType, event);
                if (creationPolicy == SagaCreationPolicy.ALWAYS
                        || (!sagaOfTypeInvoked && creationPolicy == SagaCreationPolicy.IF_NONE_FOUND)) {
                    startNewSaga(event, sagaType, associationValue);
                }
            }
        }
    }

    private boolean invokeExistingSagas(EventMessage event, Class<? extends Saga> sagaType,
                                        AssociationValue associationValue) {
        Set<String> sagas = sagaRepository.find(sagaType, associationValue);
        boolean sagaOfTypeInvoked = false;
        for (final String sagaId : sagas) {
            if (synchronizeSagaAccess) {
                lock.obtainLock(sagaId);
                Saga invokedSaga = null;
                try {
                    invokedSaga = loadAndInvoke(event, sagaId, associationValue);
                    if (invokedSaga != null) {
                        sagaOfTypeInvoked = true;
                    }
                } finally {
                    doReleaseLock(sagaId, invokedSaga);
                }
            } else {
                loadAndInvoke(event, sagaId, associationValue);
            }
        }
        return sagaOfTypeInvoked;
    }

    private void startNewSaga(EventMessage event, Class<? extends Saga> sagaType, AssociationValue associationValue) {
        Saga newSaga = sagaFactory.createSaga(sagaType);
        newSaga.getAssociationValues().add(associationValue);
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

    /**
     * Returns the Saga Creation Policy for a Saga of the given <code>sagaType</code> and <code>event</code>.
     *
     * @param sagaType The type of Saga to get the creation policy for
     * @param event    The Event that is being dispatched to Saga instances
     * @return the creation policy for the Saga
     */
    protected abstract SagaCreationPolicy getSagaCreationPolicy(Class<? extends Saga> sagaType, EventMessage event);

    /**
     * Extracts the AssociationValue from the given <code>event</code> as relevant for a Saga of given
     * <code>sagaType</code>.
     *
     * @param sagaType The type of Saga about to handle the Event
     * @param event    The event containing the association information
     * @return the AssociationValue indicating which Sagas should handle given event
     */
    protected abstract AssociationValue extractAssociationValue(Class<? extends Saga> sagaType, EventMessage event);

    private Saga loadAndInvoke(EventMessage event, String sagaId, AssociationValue association) {
        Saga saga = sagaRepository.load(sagaId);
        if (saga == null || !saga.isActive() || !saga.getAssociationValues().contains(association)) {
            return null;
        }
        try {
            try {
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
            }
        } finally {
            commit(saga);
        }
        return saga;
    }

    private void doInvokeSaga(EventMessage event, Saga saga) {
        try {
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
     * @deprecated Use {@link EventBus#unsubscribe(org.axonframework.eventhandling.EventListener)} to unsubscribe this
     *             instance
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
     * @deprecated Use {@link EventBus#subscribe(org.axonframework.eventhandling.EventListener)} to subscribe this
     *             instance
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

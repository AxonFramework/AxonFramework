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

import org.axonframework.common.Subscribable;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.axonframework.common.lock.IdentifierBasedLock;
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
    private volatile boolean suppressExceptions = true;
    private volatile boolean synchronizeSagaAccess = true;
    private final SagaHandlerExecutor executionWrapper;
    private final IdentifierBasedLock lock = new IdentifierBasedLock();

    /**
     * Initializes the SagaManager with the given <code>eventBus</code> and <code>sagaRepository</code>.
     *
     * @param eventBus       The event bus providing the events to route to sagas.
     * @param sagaRepository The repository providing the saga instances.
     * @param sagaFactory    The factory providing new saga instances
     */
    public AbstractSagaManager(EventBus eventBus, SagaRepository sagaRepository, SagaFactory sagaFactory) {
        this.eventBus = eventBus;
        this.sagaRepository = sagaRepository;
        this.sagaFactory = sagaFactory;
        this.executionWrapper = new SynchronousSagaExecutionWrapper();
    }

    @Override
    public void handle(final EventMessage event) {
        executionWrapper.scheduleLookupTask(new SagaLookupAndInvocationTask(event));
    }

    /**
     * Create a new instance of a Saga of the given <code>sagaType</code>. Resources must have been injected into the
     * Saga before returning it.
     *
     * @param sagaType The type of Saga to create an instance for
     * @param <T>      The type of Saga to create an instance for
     * @return A newly created Saga.
     */
    protected <T extends Saga> T createSaga(Class<T> sagaType) {
        return sagaFactory.createSaga(sagaType);
    }

    private void invokeSagaHandler(EventMessage event, Saga saga) {
        if (!saga.isActive()) {
            return;
        }
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
        } finally {
            commit(saga);
        }
    }

    /**
     * Finds the saga instances that the given <code>event</code> needs to be routed to. The event is sent to each of
     * the returned instances.
     *
     * @param event The event to find relevant Sagas for
     * @return The Set of relevant Sagas
     */
    protected abstract Set<Saga> findSagas(EventMessage event);

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
     */
    @Override
    @PreDestroy
    public void unsubscribe() {
        eventBus.unsubscribe(this);
    }

    /**
     * Subscribe the EventListener with the configured EventBus.
     */
    @Override
    @PostConstruct
    public void subscribe() {
        eventBus.subscribe(this);
    }

    /**
     * Returns the EventBus that delivers the events to route to Sagas.
     *
     * @return the EventBus that delivers the events to route to Sagas
     */
    protected EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Returns the repository that provides access to Saga instances.
     *
     * @return the repository that provides access to Saga instances
     */
    protected SagaRepository getSagaRepository() {
        return sagaRepository;
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

    private class SagaInvocationTask implements Runnable {

        private final Saga saga;
        private final EventMessage event;

        public SagaInvocationTask(Saga saga, EventMessage event) {
            this.saga = saga;
            this.event = event;
        }

        @Override
        public void run() {
            if (synchronizeSagaAccess) {
                lock.obtainLock(saga.getSagaIdentifier());
                try {
                    invokeSagaHandler(event, saga);
                } finally {
                    if (CurrentUnitOfWork.isStarted()) {
                        CurrentUnitOfWork.get().registerListener(new UnitOfWorkListenerAdapter() {
                            @Override
                            public void onCleanup() {
                                lock.releaseLock(saga.getSagaIdentifier());
                            }
                        });
                    } else {
                        lock.releaseLock(saga.getSagaIdentifier());
                    }
                }
            } else {
                invokeSagaHandler(event, saga);
            }
        }
    }

    private class SagaLookupAndInvocationTask implements Runnable {

        private final EventMessage event;

        public SagaLookupAndInvocationTask(EventMessage event) {
            this.event = event;
        }

        @Override
        public void run() {
            Set<Saga> sagas = findSagas(event);
            for (final Saga saga : sagas) {
                executionWrapper.scheduleEventProcessingTask(saga, new SagaInvocationTask(saga, event));
            }
        }
    }
}

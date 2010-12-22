/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.saga;

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.util.Subscribable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import static java.lang.String.format;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSagaManager implements SagaManager, Subscribable {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSagaManager.class);

    protected EventBus eventBus;
    private SagaRepository sagaRepository;
    private boolean suppressExceptions = true;

    public AbstractSagaManager(EventBus eventBus, SagaRepository sagaRepository) {
        this.eventBus = eventBus;
        this.sagaRepository = sagaRepository;
    }

    @Override
    public void handle(Event event) {
        Set<Saga> sagas = findSagas(event);
        for (Saga saga : sagas) {
            synchronized (saga) {
                if (saga.isActive()) {
                    invokeSagaHandler(event, saga);
                }
            }
        }
    }

    private void invokeSagaHandler(Event event, Saga saga) {
        try {
            saga.handle(event);
        } catch (RuntimeException e) {
            if (suppressExceptions) {
                logger.error(format("An exception occurred while a saga [%s] was handling an event [%s]:",
                                    saga.getClass().getSimpleName(),
                                    event.getClass().getSimpleName()),
                             e);
            } else {
                throw e;
            }
        } finally {
            commit(saga);
        }
    }

    protected abstract Set<Saga> findSagas(Event event);

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

    public void setSuppressExceptions(boolean suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }
}

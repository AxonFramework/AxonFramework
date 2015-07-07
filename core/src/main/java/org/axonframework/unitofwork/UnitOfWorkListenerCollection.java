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

package org.axonframework.unitofwork;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This class is responsible for notifying registered listeners in a specific order of precedence.
 * When {@link #onPrepareCommit(UnitOfWork, java.util.Set, java.util.List)}} and
 * {@link #onEventRegistered(UnitOfWork, org.axonframework.domain.EventMessage)} are called the listeners will be
 * handled in the order they have been registered (order of precedence). When {@link #afterCommit(UnitOfWork)}, {@link
 * #onRollback(UnitOfWork, Throwable)}, and {@link #onCleanup(UnitOfWork)} are called the listeners will be handled in
 * the reversed order of precedence.
 * <p/>
 * This behaviour is particularly useful because the {@link org.axonframework.auditing.AuditingUnitOfWorkListener}
 * should write an entry before any other listeners are allowed to anything (prepare commit)
 * and write an entry only after all other listeners have successfully committed (after commit).
 *
 * @author Frank Versnel
 * @since 2.0
 */
public class UnitOfWorkListenerCollection implements UnitOfWorkListener {

    private static final Logger logger = LoggerFactory.getLogger(UnitOfWorkListenerCollection.class);

    private final Deque<UnitOfWorkListener> listeners = new ArrayDeque<UnitOfWorkListener>();

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the reversed order of precedence.
     */
    @Override
    public void afterCommit(UnitOfWork unitOfWork) {
        logger.debug("Notifying listeners after commit");
        Iterator<UnitOfWorkListener> descendingIterator = listeners.descendingIterator();
        while (descendingIterator.hasNext()) {
            UnitOfWorkListener listener = descendingIterator.next();
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] after commit", listener.getClass().getName());
            }
            listener.afterCommit(unitOfWork);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the reversed order of precedence.
     */
    @Override
    public void onRollback(UnitOfWork unitOfWork, Throwable failureCause) {
        logger.debug("Notifying listeners of rollback");
        Iterator<UnitOfWorkListener> descendingIterator = listeners.descendingIterator();
        while (descendingIterator.hasNext()) {
            UnitOfWorkListener listener = descendingIterator.next();
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] of rollback", listener.getClass().getName());
            }
            listener.onRollback(unitOfWork, failureCause);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the order of precedence.
     */
    @Override
    public <T> EventMessage<T> onEventRegistered(UnitOfWork unitOfWork, EventMessage<T> event) {
        EventMessage<T> newEvent = event;
        for (UnitOfWorkListener listener : listeners) {
            newEvent = listener.onEventRegistered(unitOfWork, newEvent);
        }
        return newEvent;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the order of precedence.
     */
    @Override
    public void onPrepareCommit(UnitOfWork unitOfWork, Set<AggregateRoot> aggregateRoots, List<EventMessage> events) {
        logger.debug("Notifying listeners of commit request");
        for (UnitOfWorkListener listener : listeners) {
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] of upcoming commit", listener.getClass().getName());
            }
            listener.onPrepareCommit(unitOfWork, aggregateRoots, events);
        }
        logger.debug("Listeners successfully notified");
    }

    @Override
    public void onPrepareTransactionCommit(UnitOfWork unitOfWork, Object transaction) {
        logger.debug("Notifying listeners of transaction commit request");
        for (UnitOfWorkListener listener : listeners) {
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] of upcoming transaction commit", listener.getClass().getName());
            }
            listener.onPrepareTransactionCommit(unitOfWork, transaction);
        }
        logger.debug("Listeners successfully notified");
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the reversed order of precedence.
     */
    @Override
    public void onCleanup(UnitOfWork unitOfWork) {
        try {
            logger.debug("Notifying listeners of cleanup");
            Iterator<UnitOfWorkListener> descendingIterator = listeners.descendingIterator();
            while (descendingIterator.hasNext()) {
                UnitOfWorkListener listener = descendingIterator.next();
                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Notifying listener [{}] of cleanup", listener.getClass().getName());
                    }
                    listener.onCleanup(unitOfWork);
                } catch (RuntimeException e) {
                    logger.warn("Listener raised an exception on cleanup. Ignoring...", e);
                }
            }
            logger.debug("Listeners successfully notified");
        } finally {
            listeners.clear();
        }
    }

    /**
     * Adds a listener to the collection. Note that the order in which you register the listeners determines the order
     * in which they will be handled during the various stages of a unit of work.
     *
     * @param listener the listener to be added
     */
    public void add(UnitOfWorkListener listener) {
        if (logger.isDebugEnabled()) {
            logger.debug("Registering listener: {}", listener.getClass().getName());
        }
        listeners.add(listener);
    }
}

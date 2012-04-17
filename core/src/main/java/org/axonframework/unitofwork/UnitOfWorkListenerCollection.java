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
 * When {@link #onPrepareCommit(java.util.Set, java.util.List)}} and
 * {@link #onEventRegistered(org.axonframework.domain.EventMessage)} are called the listeners will be handled
 * in the order they have been registered (order of precedence).
 * When {@link #afterCommit()}, {@link #onRollback(Throwable)}, and {@link #onCleanup()} are called
 * the listeners will be handled in the reversed order of precedence.
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
    public void afterCommit() {
        logger.debug("Notifying listeners after commit");
        for (Iterator<UnitOfWorkListener> listenerIter = listeners.descendingIterator(); listenerIter.hasNext();) {
            UnitOfWorkListener listener = listenerIter.next();
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] after commit", listener.getClass().getName());
            }
            listener.afterCommit();
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the reversed order of precedence.
     */
    @Override
    public void onRollback(Throwable failureCause) {
        logger.debug("Notifying listeners of rollback");
        for (Iterator<UnitOfWorkListener> listenerIter = listeners.descendingIterator(); listenerIter.hasNext();) {
            UnitOfWorkListener listener = listenerIter.next();
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] of rollback", listener.getClass().getName());
            }
            listener.onRollback(failureCause);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the order of precedence.
     */
    @Override
    public <T> EventMessage<T> onEventRegistered(EventMessage<T> event) {
        EventMessage<T> newEvent = event;
        for (UnitOfWorkListener listener : listeners) {
            newEvent = listener.onEventRegistered(event);
        }
        return newEvent;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the order of precedence.
     */
    @Override
    public void onPrepareCommit(Set<AggregateRoot> aggregateRoots, List<EventMessage> events) {
        logger.debug("Notifying listeners of commit request");
        for (UnitOfWorkListener listener : listeners) {
            if (logger.isDebugEnabled()) {
                logger.debug("Notifying listener [{}] of upcoming commit", listener.getClass().getName());
            }
            listener.onPrepareCommit(aggregateRoots, events);
        }
        logger.debug("Listeners successfully notified");
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Listeners are called in the reversed order of precedence.
     */
    @Override
    public void onCleanup() {
        logger.debug("Notifying listeners of cleanup");
        for (Iterator<UnitOfWorkListener> listenerIter = listeners.descendingIterator(); listenerIter.hasNext();) {
            UnitOfWorkListener listener = listenerIter.next();
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Notifying listener [{}] of cleanup", listener.getClass().getName());
                }
                listener.onCleanup();
            } catch (RuntimeException e) {
                logger.warn("Listener raised an exception on cleanup. Ignoring...", e);
            }
        }
        logger.debug("Listeners successfully notified");
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

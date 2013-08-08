package org.axonframework.unitofwork.nesting;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.SaveAggregateCallback;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListener;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.junit.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class UnitOfWorkNestingTest {

    @Test
    public void testUowRolledBackOnOuterRollback() {
        UnitOfWork outerUnit = new UnitOfWork() {

            private boolean started = false;
            private List<UnitOfWorkListener> listeners = new ArrayList<UnitOfWorkListener>();

            @Override
            public void commit() {
                for (UnitOfWorkListener listener : listeners) {
                    listener.onPrepareCommit(this,
                                             Collections.<AggregateRoot>emptySet(),
                                             Collections.<EventMessage>emptyList());
                    listener.afterCommit(this);
                    listener.onCleanup(this);
                }
                started = false;
                CurrentUnitOfWork.clear(this);
            }

            @Override
            public void rollback() {
                rollback(null);
            }

            @Override
            public void rollback(Throwable cause) {
                if (started) {
                    for (UnitOfWorkListener listener : listeners) {
                        listener.onRollback(this, cause);
                        listener.onCleanup(this);
                    }
                    started = false;
                    CurrentUnitOfWork.clear(this);
                }
            }

            @Override
            public void start() {
                CurrentUnitOfWork.set(this);
                started = true;
            }

            @Override
            public boolean isStarted() {
                return started;
            }

            @Override
            public boolean isTransactional() {
                return false;
            }

            @Override
            public void registerListener(UnitOfWorkListener listener) {
                this.listeners.add(listener);
            }

            @Override
            public <T extends AggregateRoot> T registerAggregate(T aggregateRoot, EventBus eventBus,
                                                                 SaveAggregateCallback<T> saveAggregateCallback) {
                return aggregateRoot;
            }

            @Override
            public void publishEvent(EventMessage<?> event, EventBus eventBus) {
            }
        }; // This is a unit that does not extend from NestableUnitOfWork

        outerUnit.start();
        UnitOfWork middleUnit = DefaultUnitOfWork.startAndGet();
        UnitOfWork innerUnit = DefaultUnitOfWork.startAndGet();

        final Set<UnitOfWork> rolledBack = new HashSet<UnitOfWork>();
        final UnitOfWorkListenerAdapter listener = new UnitOfWorkListenerAdapter() {
            @Override
            public void onRollback(UnitOfWork unitOfWork, Throwable failureCause) {
                rolledBack.add(unitOfWork);
            }
        };
        final UnitOfWorkListener middleListener = mock(UnitOfWorkListener.class, "middleListener");
        final UnitOfWorkListener innerListener = mock(UnitOfWorkListener.class, "innerListener");
        final UnitOfWorkListener outerListener = mock(UnitOfWorkListener.class, "outerListener");

        outerUnit.registerListener(outerListener);
        middleUnit.registerListener(listener);
        innerUnit.registerListener(listener);
        middleUnit.registerListener(middleListener);
        innerUnit.registerListener(innerListener);

        innerUnit.commit();
        middleUnit.commit();

        verify(innerListener, never()).afterCommit(any(UnitOfWork.class));
        verify(middleListener, never()).afterCommit(any(UnitOfWork.class));

        outerUnit.rollback();

        InOrder inOrder = inOrder(middleListener, innerListener, outerListener);
        inOrder.verify(innerListener).onPrepareCommit(any(UnitOfWork.class), anySet(), anyList());
        inOrder.verify(middleListener).onPrepareCommit(any(UnitOfWork.class), anySet(), anyList());
        inOrder.verify(innerListener).onRollback(any(UnitOfWork.class), any(Throwable.class));
        inOrder.verify(middleListener).onRollback(any(UnitOfWork.class), any(Throwable.class));
        inOrder.verify(outerListener).onRollback(any(UnitOfWork.class), any(Throwable.class));

        // we don't really care when the cleanup is invoked
        verify(innerListener).onCleanup(any(UnitOfWork.class));
        verify(middleListener).onCleanup(any(UnitOfWork.class));
        verify(outerListener).onCleanup(any(UnitOfWork.class));

        assertEquals("Expected inner Unit of Work to have been rolled back", 2, rolledBack.size());
        assertFalse("Expected all UoW to have been cleared", CurrentUnitOfWork.isStarted());
    }
}

/*
 * Copyright (c) 2010-2011. Axon Framework
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
import org.axonframework.domain.Event;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.junit.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class DefaultUnitOfWorkTest {

    private DefaultUnitOfWork testSubject;
    private EventBus mockEventBus;
    private AggregateRoot mockAggregateRoot;
    private Event event1 = new StubDomainEvent(1);
    private Event event2 = new StubDomainEvent(2);
    private EventListener listener1;
    private EventListener listener2;
    private SaveAggregateCallback callback;

    @SuppressWarnings({"unchecked", "deprecation"})
    @Before
    public void setUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        testSubject = new DefaultUnitOfWork();
        mockEventBus = mock(EventBus.class);
        mockAggregateRoot = mock(AggregateRoot.class);
        listener1 = mock(EventListener.class, "listener1");
        listener2 = mock(EventListener.class, "listener2");
        callback = mock(SaveAggregateCallback.class);
        doAnswer(new PublishEvent(event1)).doAnswer(new PublishEvent(event2))
                .when(callback).save(mockAggregateRoot);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                listener1.handle((Event) invocation.getArguments()[0]);
                listener2.handle((Event) invocation.getArguments()[0]);
                return null;
            }
        }).when(mockEventBus).publish(isA(Event.class));
    }

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testUnitOfWorkRegistersListenerWithParent() {
        UnitOfWork parentUoW = mock(UnitOfWork.class);
        CurrentUnitOfWork.set(parentUoW);
        UnitOfWork innerUow = DefaultUnitOfWork.startAndGet();
        innerUow.rollback();
        parentUoW.rollback();
        CurrentUnitOfWork.clear(parentUoW);
        verify(parentUoW).registerListener(isA(UnitOfWorkListener.class));
    }

    @Test
    public void testInnerUnitOfWorkRolledBackWithOuter() {
        final AtomicBoolean isRolledBack = new AtomicBoolean(false);
        UnitOfWork outer = DefaultUnitOfWork.startAndGet();
        UnitOfWork inner = DefaultUnitOfWork.startAndGet();
        inner.registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void onRollback(Throwable failureCause) {
                isRolledBack.set(true);
            }
        });
        inner.rollback();
        outer.rollback();
        assertTrue("The inner UoW wasn't properly rolled back", isRolledBack.get());
        assertFalse("The UnitOfWork haven't been correctly cleared", CurrentUnitOfWork.isStarted());
    }

    @Test
    public void testInnerUnitOfWorkCommittedBackWithOuter() {
        final AtomicBoolean isCommitted = new AtomicBoolean(false);
        UnitOfWork outer = DefaultUnitOfWork.startAndGet();
        UnitOfWork inner = DefaultUnitOfWork.startAndGet();
        inner.registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void afterCommit() {
                isCommitted.set(true);
            }
        });
        inner.commit();
        assertFalse("The inner UoW was committed prematurely", isCommitted.get());
        outer.commit();
        assertTrue("The inner UoW wasn't properly committed", isCommitted.get());
        assertFalse("The UnitOfWork haven't been correctly cleared", CurrentUnitOfWork.isStarted());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testSagaEventsDoNotOvertakeRegularEvents() {
        testSubject.start();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                DefaultUnitOfWork uow = new DefaultUnitOfWork();
                uow.start();
                uow.registerAggregate(mockAggregateRoot, callback);
                uow.commit();
                return null;
            }
        }).when(listener1).handle(event1);
        testSubject.registerAggregate(mockAggregateRoot, callback);
        testSubject.commit();

        InOrder inOrder = inOrder(listener1, listener2, callback);
        inOrder.verify(listener1, times(1)).handle(event1);
        inOrder.verify(listener2, times(1)).handle(event1);
        inOrder.verify(listener1, times(1)).handle(event2);
        inOrder.verify(listener2, times(1)).handle(event2);
    }

    @Test
    public void testUnitOfWorkRolledBackOnCommitFailure_ErrorOnPrepareCommit() {
        UnitOfWorkListener mockListener = mock(UnitOfWorkListener.class);
        doThrow(new RuntimeException("Mock")).when(mockListener).onPrepareCommit(anySetOf(AggregateRoot.class),
                                                                                 anyListOf(Event.class));
        testSubject.registerListener(mockListener);
        testSubject.start();
        try {
            testSubject.commit();
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertEquals("Got an exception, but the wrong one", RuntimeException.class, e.getClass());
            assertEquals("Got an exception, but the wrong one", "Mock", e.getMessage());
        }
        verify(mockListener).onRollback(isA(RuntimeException.class));
        verify(mockListener, never()).afterCommit();
        verify(mockListener).onCleanup();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testUnitOfWorkRolledBackOnCommitFailure_ErrorOnCommitAggregate() {
        UnitOfWorkListener mockListener = mock(UnitOfWorkListener.class);
        doThrow(new RuntimeException("Mock")).when(callback).save(isA(AggregateRoot.class));
        testSubject.registerListener(mockListener);
        testSubject.registerAggregate(mockAggregateRoot, callback);
        testSubject.start();
        try {
            testSubject.commit();
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertEquals("Got an exception, but the wrong one", RuntimeException.class, e.getClass());
            assertEquals("Got an exception, but the wrong one", "Mock", e.getMessage());
        }
        verify(mockListener).onPrepareCommit(anySetOf(AggregateRoot.class), anyListOf(Event.class));
        verify(mockListener).onRollback(isA(RuntimeException.class));
        verify(mockListener, never()).afterCommit();
        verify(mockListener).onCleanup();
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testUnitOfWorkRolledBackOnCommitFailure_ErrorOnDispatchEvents() {
        UnitOfWorkListener mockListener = mock(UnitOfWorkListener.class);
        doThrow(new RuntimeException("Mock")).when(mockEventBus).publish(isA(Event.class));
        testSubject.start();
        testSubject.registerListener(mockListener);
        testSubject.publishEvent(new StubDomainEvent(), mockEventBus);
        try {
            testSubject.commit();
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertThat(e, new ArgumentMatcher<RuntimeException>() {
                @Override
                public boolean matches(Object o) {
                    return "Mock".equals(((RuntimeException) o).getMessage());
                }
            });
            assertEquals("Got an exception, but the wrong one", RuntimeException.class, e.getClass());
            assertEquals("Got an exception, but the wrong one", "Mock", e.getMessage());
        }
        verify(mockListener).onPrepareCommit(anySetOf(AggregateRoot.class), anyListOf(Event.class));
        verify(mockListener).onRollback(isA(RuntimeException.class));
        verify(mockListener, never()).afterCommit();
        verify(mockListener).onCleanup();
    }

    private class PublishEvent implements Answer {

        private final Event event;

        private PublishEvent(Event event) {
            this.event = event;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            CurrentUnitOfWork.get().publishEvent(event, mockEventBus);
            return null;
        }
    }
}

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

package org.axonframework.unitofwork;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.testutils.MockException;
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
    private EventMessage event1 = new GenericEventMessage<Integer>(1);
    private EventMessage event2 = new GenericEventMessage<Integer>(2);
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
                listener1.handle((EventMessage) invocation.getArguments()[0]);
                listener2.handle((EventMessage) invocation.getArguments()[0]);
                return null;
            }
        }).when(mockEventBus).publish(isA(EventMessage.class));
    }

    @After
    public void tearDown() {
        assertFalse("A UnitOfWork was not properly cleared", CurrentUnitOfWork.isStarted());
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
            public void onRollback(UnitOfWork unitOfWork, Throwable failureCause) {
                isRolledBack.set(true);
            }
        });
        inner.commit();
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
            public void afterCommit(UnitOfWork unitOfWork) {
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
                uow.registerAggregate(mockAggregateRoot, mockEventBus, callback);
                uow.commit();
                return null;
            }
        }).when(listener1).handle(event1);
        testSubject.registerAggregate(mockAggregateRoot, mockEventBus, callback);
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
        doThrow(new MockException()).when(mockListener).onPrepareCommit(isA(UnitOfWork.class),
                                                                        anySetOf(AggregateRoot.class),
                                                                        anyListOf(EventMessage.class));
        testSubject.registerListener(mockListener);
        testSubject.start();
        try {
            testSubject.commit();
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertEquals("Got an exception, but the wrong one", MockException.class, e.getClass());
            assertEquals("Got an exception, but the wrong one", "Mock", e.getMessage());
        }
        verify(mockListener).onRollback(isA(UnitOfWork.class), isA(RuntimeException.class));
        verify(mockListener, never()).afterCommit(isA(UnitOfWork.class));
        verify(mockListener).onCleanup(isA(UnitOfWork.class));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testUnitOfWorkRolledBackOnCommitFailure_ErrorOnCommitAggregate() {
        UnitOfWorkListener mockListener = mock(UnitOfWorkListener.class);
        doThrow(new MockException()).when(callback).save(isA(AggregateRoot.class));
        testSubject.registerListener(mockListener);
        testSubject.registerAggregate(mockAggregateRoot, mockEventBus, callback);
        testSubject.start();
        try {
            testSubject.commit();
            fail("Expected exception");
        } catch (RuntimeException e) {
            assertEquals("Got an exception, but the wrong one", MockException.class, e.getClass());
            assertEquals("Got an exception, but the wrong one", "Mock", e.getMessage());
        }
        verify(mockListener).onPrepareCommit(isA(UnitOfWork.class), anySetOf(AggregateRoot.class), anyListOf(
                EventMessage.class));
        verify(mockListener).onRollback(isA(UnitOfWork.class), isA(RuntimeException.class));
        verify(mockListener, never()).afterCommit(isA(UnitOfWork.class));
        verify(mockListener).onCleanup(isA(UnitOfWork.class));
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testUnitOfWorkRolledBackOnCommitFailure_ErrorOnDispatchEvents() {
        UnitOfWorkListener mockListener = mock(UnitOfWorkListener.class);
        when(mockListener.onEventRegistered(isA(UnitOfWork.class), Matchers.<EventMessage<Object>>any()))
                .thenAnswer(new ReturnParameterAnswer(1));

        doThrow(new MockException()).when(mockEventBus).publish(isA(EventMessage.class));
        testSubject.start();
        testSubject.registerListener(mockListener);
        testSubject.publishEvent(new GenericEventMessage<Object>(new Object()), mockEventBus);
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
            assertEquals("Got an exception, but the wrong one", MockException.class, e.getClass());
            assertEquals("Got an exception, but the wrong one", "Mock", e.getMessage());
        }
        verify(mockListener).onPrepareCommit(isA(UnitOfWork.class), anySetOf(AggregateRoot.class),
                                             anyListOf(EventMessage.class));
        verify(mockListener).onRollback(isA(UnitOfWork.class), isA(RuntimeException.class));
        verify(mockListener, never()).afterCommit(isA(UnitOfWork.class));
        verify(mockListener).onCleanup(isA(UnitOfWork.class));
    }

    @SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored"})
    @Test
    public void testUnitOfWorkCleanupDelayedUntilOuterUnitOfWorkIsCleanedUp_InnerCommit() {
        UnitOfWorkListener outerListener = mock(UnitOfWorkListener.class);
        UnitOfWorkListener innerListener = mock(UnitOfWorkListener.class);
        UnitOfWork outer = DefaultUnitOfWork.startAndGet();
        UnitOfWork inner = DefaultUnitOfWork.startAndGet();
        inner.registerListener(innerListener);
        outer.registerListener(outerListener);
        inner.commit();
        verify(innerListener, never()).afterCommit(isA(UnitOfWork.class));
        verify(innerListener, never()).onCleanup(isA(UnitOfWork.class));
        outer.commit();

        InOrder inOrder = inOrder(innerListener, outerListener);
        inOrder.verify(innerListener).afterCommit(isA(UnitOfWork.class));
        inOrder.verify(outerListener).afterCommit(isA(UnitOfWork.class));
        inOrder.verify(innerListener).onCleanup(isA(UnitOfWork.class));
        inOrder.verify(outerListener).onCleanup(isA(UnitOfWork.class));
    }

    @SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored", "NullableProblems"})
    @Test
    public void testUnitOfWorkCleanupDelayedUntilOuterUnitOfWorkIsCleanedUp_InnerRollback() {
        UnitOfWorkListener outerListener = mock(UnitOfWorkListener.class);
        UnitOfWorkListener innerListener = mock(UnitOfWorkListener.class);
        UnitOfWork outer = DefaultUnitOfWork.startAndGet();
        UnitOfWork inner = DefaultUnitOfWork.startAndGet();
        inner.registerListener(innerListener);
        outer.registerListener(outerListener);
        inner.rollback();
        verify(innerListener, never()).afterCommit(isA(UnitOfWork.class));
        verify(innerListener, never()).onCleanup(isA(UnitOfWork.class));
        outer.commit();

        InOrder inOrder = inOrder(innerListener, outerListener);
        inOrder.verify(innerListener).onRollback(isA(UnitOfWork.class), (Throwable) isNull());
        inOrder.verify(outerListener).afterCommit(isA(UnitOfWork.class));
        inOrder.verify(innerListener).onCleanup(isA(UnitOfWork.class));
        inOrder.verify(outerListener).onCleanup(isA(UnitOfWork.class));
    }

    @SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored", "NullableProblems"})
    @Test
    public void testUnitOfWorkCleanupDelayedUntilOuterUnitOfWorkIsCleanedUp_InnerCommit_OuterRollback() {
        UnitOfWorkListener outerListener = mock(UnitOfWorkListener.class);
        UnitOfWorkListener innerListener = mock(UnitOfWorkListener.class);
        UnitOfWork outer = DefaultUnitOfWork.startAndGet();
        UnitOfWork inner = DefaultUnitOfWork.startAndGet();
        inner.registerListener(innerListener);
        outer.registerListener(outerListener);
        inner.commit();
        verify(innerListener, never()).afterCommit(isA(UnitOfWork.class));
        verify(innerListener, never()).onCleanup(isA(UnitOfWork.class));
        outer.rollback();
        verify(outerListener, never()).onPrepareCommit(isA(UnitOfWork.class),
                                                       anySetOf(AggregateRoot.class),
                                                       anyListOf(EventMessage.class));

        InOrder inOrder = inOrder(innerListener, outerListener);
        inOrder.verify(innerListener).onPrepareCommit(isA(UnitOfWork.class),
                                                      anySetOf(AggregateRoot.class),
                                                      anyListOf(EventMessage.class));

        inOrder.verify(innerListener).onRollback(isA(UnitOfWork.class), (Throwable) isNull());
        inOrder.verify(outerListener).onRollback(isA(UnitOfWork.class), (Throwable) isNull());
        inOrder.verify(innerListener).onCleanup(isA(UnitOfWork.class));
        inOrder.verify(outerListener).onCleanup(isA(UnitOfWork.class));
    }

    private static class ReturnParameterAnswer implements Answer<Object> {

        private final int parameterIndex;

        private ReturnParameterAnswer(int parameterIndex) {
            this.parameterIndex = parameterIndex;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            return invocation.getArguments()[parameterIndex];
        }
    }

    private class PublishEvent implements Answer {

        private final EventMessage event;

        private PublishEvent(EventMessage event) {
            this.event = event;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            CurrentUnitOfWork.get().publishEvent(event, mockEventBus);
            return null;
        }
    }
}

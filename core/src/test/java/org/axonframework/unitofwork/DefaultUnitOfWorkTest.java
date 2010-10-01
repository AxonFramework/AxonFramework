/*
 * Copyright (c) 2010. Axon Framework
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

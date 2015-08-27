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

package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class AbstractEventBusTest {

    private UnitOfWork unitOfWork;
    private StubPublishingEventBus testSubject;

    @Before
    public void setUp() {
        (unitOfWork = spy(new DefaultUnitOfWork(null))).start();
        testSubject = spy(new StubPublishingEventBus());
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testConsumersRegisteredWithUnitOfWorkWhenFirstEventIsPublished() {
        EventMessage<?> event = newEvent();
        testSubject.publish(event);
        verify(unitOfWork).onPrepareCommit(any());
        verify(unitOfWork).onCommit(any());
        verify(unitOfWork).afterCommit(any());
        List<EventMessage<?>> actual = unitOfWork.getResource(testSubject.eventsKey);
        assertEquals(Collections.singletonList(event), actual);
    }

    @Test
    public void testNoMoreConsumersRegisteredWithUnitOfWorkWhenSecondEventIsPublished() {
        EventMessage<?> event = newEvent();
        testSubject.publish(event);
        reset(unitOfWork);

        testSubject.publish(event);
        verify(unitOfWork, never()).onPrepareCommit(any());
        verify(unitOfWork, never()).onCommit(any());
        verify(unitOfWork, never()).afterCommit(any());
        List<EventMessage<?>> actual = unitOfWork.getResource(testSubject.eventsKey);
        assertEquals(Arrays.asList(event, event), actual);
    }

    @Test
    public void testCommitOnUnitOfWork() {
        EventMessage<?> event = newEvent();
        testSubject.publish(event);
        unitOfWork.commit();
        assertEquals(Collections.singletonList(event), testSubject.committedEvents);
    }

    @Test
    public void testPublicationOrder() {
        EventMessage<?> eventA = newEvent(), eventB = newEvent();
        testSubject.publish(eventA);
        testSubject.publish(eventB);
        unitOfWork.commit();
        assertEquals(Arrays.asList(eventA, eventB), testSubject.committedEvents);
    }

    @Test
     public void testPublicationWithNestedUow() {
        testSubject.publish(numberedEvent(5));
        unitOfWork.commit();
        assertEquals(Arrays.asList(numberedEvent(5), numberedEvent(4), numberedEvent(3), numberedEvent(2),
                numberedEvent(1), numberedEvent(0)), testSubject.committedEvents);
        verify(testSubject, times(6)).prepareCommit(any());
        verify(testSubject, times(6)).commit(any());
        verify(testSubject, times(6)).afterCommit(any());

        verify(unitOfWork, times(6)).onPrepareCommit(any());
        verify(unitOfWork, times(6)).onCommit(any());
    }

    @Test
    public void testPublicationWithNestedUowAfterRootUowIsCommitted() {
        testSubject = spy(new StubPublishingEventBus(UnitOfWork.Phase.COMMIT, true));
        testSubject.publish(numberedEvent(5));
        unitOfWork.commit();
        assertEquals(Arrays.asList(numberedEvent(5), numberedEvent(4), numberedEvent(3), numberedEvent(2),
                numberedEvent(1), numberedEvent(0)), testSubject.committedEvents);
        verify(testSubject, times(6)).prepareCommit(any());
        verify(testSubject, times(6)).commit(any());
        verify(testSubject, times(6)).afterCommit(any());

        verify(unitOfWork, times(1)).onPrepareCommit(any());
        verify(unitOfWork, times(6)).onCommit(any());
    }

    @Test(expected = IllegalStateException.class)
    public void testPublicationForbiddenDuringUowCommitPhase() {
        new StubPublishingEventBus(UnitOfWork.Phase.COMMIT, false).publish(numberedEvent(5));
        unitOfWork.commit();
    }

    private static EventMessage newEvent() {
        return new GenericEventMessage<>(new Object());
    }

    private static EventMessage numberedEvent(final int number) {
        return new StubNumberedEvent(number);
    }

    private static class StubPublishingEventBus extends AbstractEventBus {

        private final List<EventMessage<?>> committedEvents = new ArrayList<>();
        private final boolean startNewUowBeforePublishing;
        private final UnitOfWork.Phase publicationPhase;

        public StubPublishingEventBus() {
            this(UnitOfWork.Phase.PREPARE_COMMIT, true);
        }

        public StubPublishingEventBus(UnitOfWork.Phase publicationPhase, boolean startNewUowBeforePublishing) {
            this.startNewUowBeforePublishing = startNewUowBeforePublishing;
            this.publicationPhase = publicationPhase;
        }

        @Override
        protected void prepareCommit(List<EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.PREPARE_COMMIT) {
                onEvents(events);
            }
        }

        @Override
        protected void commit(List<EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.COMMIT) {
                onEvents(events);
            }
        }

        @Override
        protected void afterCommit(List<EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.AFTER_COMMIT) {
                onEvents(events);
            }
        }

        private void onEvents(List<EventMessage<?>> events) {
            //if the event payload is a number > 0, a new number is published that is 1 smaller than the first number
            Object payload = events.get(0).getPayload();
            if (payload instanceof Integer) {
                int number = (int) payload;
                if (number > 0) {
                    EventMessage nextEvent = numberedEvent(number - 1);
                    if (startNewUowBeforePublishing) {
                        UnitOfWork nestedUnitOfWork = DefaultUnitOfWork.startAndGet(null);
                        try {
                            publish(nextEvent);
                        } finally {
                            nestedUnitOfWork.commit();
                        }
                    } else {
                        publish(nextEvent);
                    }
                }
            }
            committedEvents.addAll(events);
        }

        @Override
        public void subscribe(Cluster cluster) {
        }

        @Override
        public void unsubscribe(Cluster cluster) {
        }
    }

    private static class StubNumberedEvent extends GenericEventMessage<Integer> {
        public StubNumberedEvent(Integer payload) {
            super(payload);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StubNumberedEvent that = (StubNumberedEvent) o;
            return Objects.equals(getPayload(), that.getPayload());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getPayload());
        }
    }

}

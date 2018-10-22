/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
public class AbstractEventBusTest {

    private UnitOfWork<?> unitOfWork;
    private StubPublishingEventBus testSubject;

    @Before
    public void setUp() {
        (unitOfWork = spy(new DefaultUnitOfWork<>(null))).start();
        testSubject = spy(StubPublishingEventBus.builder().build());
    }

    @After
    public void tearDown() {
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
        // the monitor callback is also registered
        verify(unitOfWork, times(2)).afterCommit(any());
        assertEquals(emptyList(), testSubject.committedEvents);
        unitOfWork.commit();
        assertEquals(Collections.singletonList(event), testSubject.committedEvents);
    }

    @Test
    public void testNoMoreConsumersRegisteredWithUnitOfWorkWhenSecondEventIsPublished() {
        EventMessage<?> event = newEvent();
        testSubject.publish(event);
        verify(unitOfWork).onPrepareCommit(any());
        verify(unitOfWork).onCommit(any());
        // the monitor callback is also registered
        verify(unitOfWork, times(2)).afterCommit(any());
        reset(unitOfWork);

        testSubject.publish(event);
        verify(unitOfWork, never()).onPrepareCommit(any());
        verify(unitOfWork, never()).onCommit(any());
        // the monitor callback should still be registered
        verify(unitOfWork).afterCommit(any());

        unitOfWork.commit();
        List<EventMessage<?>> actual = testSubject.committedEvents;
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

        // each UoW will register onPrepareCommit on the parent
        verify(unitOfWork, times(1)).onPrepareCommit(any());
        // each UoW will register onCommit with the root
        verify(unitOfWork, times(6)).onCommit(any());
    }

    @Test(expected = IllegalStateException.class)
    public void testPublicationForbiddenDuringUowCommitPhase() {
        StubPublishingEventBus.builder()
                              .publicationPhase(UnitOfWork.Phase.COMMIT)
                              .startNewUowBeforePublishing(false)
                              .build()
                              .publish(numberedEvent(5));
        unitOfWork.commit();
    }

    @Test(expected = IllegalStateException.class)
    public void testPublicationForbiddenDuringRootUowCommitPhase() {
        testSubject = spy(StubPublishingEventBus.builder().publicationPhase(UnitOfWork.Phase.COMMIT).build());
        testSubject.publish(numberedEvent(1));
        unitOfWork.commit();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDispatchInterceptor() {
        MessageDispatchInterceptor<EventMessage<?>> dispatchInterceptorMock = mock(MessageDispatchInterceptor.class);
        String key = "additional", value = "metaData";
        when(dispatchInterceptorMock.handle(anyList())).thenAnswer(invocation -> {
            List<EventMessage<?>> eventMessages = (List<EventMessage<?>>) invocation.getArguments()[0];
            return (BiFunction<Integer, Object, Object>) (index, message) -> {
                if (eventMessages.get(index).getMetaData().containsKey(key)) {
                    throw new AssertionError("MessageProcessor is asked to process the same event message twice");
                }
                return eventMessages.get(index).andMetaData(Collections.singletonMap(key, value));
            };
        });
        testSubject.registerDispatchInterceptor(dispatchInterceptorMock);
        testSubject.publish(newEvent(), newEvent());
        verifyZeroInteractions(dispatchInterceptorMock);

        unitOfWork.commit();
        ArgumentCaptor<List> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatchInterceptorMock).handle(argumentCaptor.capture()); //prepare commit, commit, and after commit
        assertEquals(1, argumentCaptor.getAllValues().size());
        assertEquals(2, argumentCaptor.getValue().size());
        assertEquals(value, ((EventMessage<?>) argumentCaptor.getValue().get(0)).getMetaData().get(key));
    }

    private static EventMessage newEvent() {
        return new GenericEventMessage<>(new Object());
    }

    private static EventMessage numberedEvent(final int number) {
        return new StubNumberedEvent(number);
    }

    private static class StubPublishingEventBus extends AbstractEventBus {

        private final List<EventMessage<?>> committedEvents = new ArrayList<>();

        private final UnitOfWork.Phase publicationPhase;
        private final boolean startNewUowBeforePublishing;

        private StubPublishingEventBus(Builder builder) {
            super(builder);
            this.publicationPhase = builder.publicationPhase;
            this.startNewUowBeforePublishing = builder.startNewUowBeforePublishing;
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        protected void prepareCommit(List<? extends EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.PREPARE_COMMIT) {
                onEvents(events);
            }
        }

        @Override
        protected void commit(List<? extends EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.COMMIT) {
                onEvents(events);
            }
        }

        @Override
        protected void afterCommit(List<? extends EventMessage<?>> events) {
            if (publicationPhase == UnitOfWork.Phase.AFTER_COMMIT) {
                onEvents(events);
            }
        }

        private void onEvents(List<? extends EventMessage<?>> events) {
            //if the event payload is a number > 0, a new number is published that is 1 smaller than the first number
            Object payload = events.get(0).getPayload();
            if (payload instanceof Integer) {
                int number = (int) payload;
                if (number > 0) {
                    EventMessage nextEvent = numberedEvent(number - 1);
                    if (startNewUowBeforePublishing) {
                        UnitOfWork<?> nestedUnitOfWork = DefaultUnitOfWork.startAndGet(null);
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
        public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        private static class Builder extends AbstractEventBus.Builder {

            private UnitOfWork.Phase publicationPhase = UnitOfWork.Phase.PREPARE_COMMIT;
            private boolean startNewUowBeforePublishing = true;

            private Builder publicationPhase(UnitOfWork.Phase publicationPhase) {
                this.publicationPhase = publicationPhase;
                return this;
            }

            private Builder startNewUowBeforePublishing(boolean startNewUowBeforePublishing) {
                this.startNewUowBeforePublishing = startNewUowBeforePublishing;
                return this;
            }

            private StubPublishingEventBus build() {
                return new StubPublishingEventBus(this);
            }
        }
    }

    private static class StubNumberedEvent extends GenericEventMessage<Integer> {

        public StubNumberedEvent(Integer payload) {
            super(payload);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            StubNumberedEvent that = (StubNumberedEvent) o;
            return Objects.equals(getPayload(), that.getPayload());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getPayload());
        }

        @Override
        public String toString() {
            return "StubNumberedEvent{" + getPayload() + "}";
        }
    }
}
